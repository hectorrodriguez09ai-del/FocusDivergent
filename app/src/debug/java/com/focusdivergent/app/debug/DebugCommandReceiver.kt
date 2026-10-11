package com.focusdivergent.app.debug

import android.content.Context
import android.content.Intent
import com.focusdivergent.app.data.local.dao.TimeExtensionDao
import com.focusdivergent.app.data.settings.SharedPreferencesRealityCheckSettings
import com.focusdivergent.app.domain.closure.DayClosureSettings
import com.focusdivergent.app.domain.closure.DayClosureState
import com.focusdivergent.app.domain.model.AppLimit
import com.focusdivergent.app.domain.model.EnergyLevel
import com.focusdivergent.app.domain.model.Task
import com.focusdivergent.app.domain.policy.RealityCheckPolicy
import com.focusdivergent.app.domain.model.TaskType
import com.focusdivergent.app.domain.repository.AppLimitRepository
import com.focusdivergent.app.domain.repository.PendingLimitChangeRepository
import com.focusdivergent.app.domain.repository.TaskRepository
import com.focusdivergent.app.domain.usecase.RunDayClosureUseCase
import com.focusdivergent.app.domain.usecase.ScheduleDayClosureUseCase
import com.focusdivergent.app.service.HiltBroadcastReceiver
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Órdenes de depuración desde ADB (solo en builds de debug). Ejemplos:
 *
 * ```
 * adb shell am broadcast -n com.focusdivergent.app/.debug.DebugCommandReceiver \
 *     -a com.focusdivergent.app.debug.SET_LIMIT --es package com.example.app --ei minutes 1
 * adb shell am broadcast -n com.focusdivergent.app/.debug.DebugCommandReceiver \
 *     -a com.focusdivergent.app.debug.CLEAR_LIMIT --es package com.example.app
 * ```
 *
 * `SET_LIMIT` acepta además `--ez strict true` para el bloqueo estricto y `--ei extensions N` (0 = nivel firme). `RESET_EXTENSIONS --es package ...`
 * borra las ampliaciones de tiempo ganadas, para repetir la prueba del tope diario.
 *
 * `CREATE_OBLIGATION --ei minutes N [--es title "Cita médica"]` crea una Obligación que empieza dentro de N
 * minutos (su aviso salta N−10 minutos después de crearla). `CLEAR_OBLIGATIONS` borra todas las Obligaciones.
 *
 * `SET_REALITY_CHECK --ei minutes N [--ei silence M]` acorta el Reality Check a N minutos seguidos (y M de
 * silencio tras responder que sí, por defecto N); `RESET_REALITY_CHECK` vuelve a los 20 minutos de serie.
 *
 * `RUN_CLOSURE` cierra la jornada de hoy ahora mismo (traslada lo pendiente, avisa con la notificación y anota el cierre).
 * `SET_CLOSURE [--ez enabled true] [--ei close_hour H --ei close_minute M] [--ei lift_hour L]` fija la configuración del Cierre
 * al instante, saltándose la regla de medianoche. `SET_LEISURE --es package P --ez leisure true` marca (o desmarca) una app
 * con límite como «de ocio», también al instante.
 *
 * `EXPIRE_PENDING_LIMITS` adelanta a «ahora» los cambios de límite que esperan a medianoche, para probar que
 * entran en vigor sin esperar al día siguiente.
 *
 * El resultado se imprime como `data=`.
 */
@AndroidEntryPoint
class DebugCommandReceiver : HiltBroadcastReceiver() {

    @Inject lateinit var limits: AppLimitRepository
    @Inject lateinit var tasks: TaskRepository
    @Inject lateinit var pendingChanges: PendingLimitChangeRepository
    @Inject lateinit var clock: Clock
    @Inject lateinit var realityCheck: SharedPreferencesRealityCheckSettings
    @Inject lateinit var runClosure: RunDayClosureUseCase
    @Inject lateinit var closureSettings: DayClosureSettings
    @Inject lateinit var scheduleClosure: ScheduleDayClosureUseCase

    // Se usa el DAO directamente: la app nunca borra ampliaciones, solo esta herramienta de pruebas.
    @Inject lateinit var extensions: TimeExtensionDao

    override fun onReceive(context: Context, intent: Intent) {
        // Hilt inyecta en la clase base: hay que llamarla antes de usar cualquier dependencia.
        super.onReceive(context, intent)

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val result = try {
                run(intent)
            } catch (e: Exception) {
                "ERROR: ${e.message}"
            }
            pending.setResultData(result)
            pending.finish()
        }
    }

    private suspend fun run(intent: Intent): String {
        val packageName = intent.getStringExtra(EXTRA_PACKAGE)
        return when (intent.action) {
            ACTION_SET_LIMIT -> {
                if (packageName.isNullOrBlank()) return MISSING_PACKAGE
                val minutes = intent.getIntExtra(EXTRA_MINUTES, 1)
                val strict = intent.getBooleanExtra(EXTRA_STRICT, false)
                val extensions = intent.getIntExtra(EXTRA_EXTENSIONS, AppLimit.DEFAULT_EXTENSIONS_PER_DAY)
                limits.save(AppLimit(packageName, minutes, strict, extensions))
                "OK: $packageName limitada a $minutes min (estricta=$strict, ampliaciones=$extensions)"
            }
            ACTION_CLEAR_LIMIT -> {
                if (packageName.isNullOrBlank()) return MISSING_PACKAGE
                val existing = limits.getByPackage(packageName)
                if (existing != null) limits.delete(existing)
                if (existing != null) "OK: limite de $packageName eliminado" else "OK: $packageName no tenia limite"
            }
            ACTION_RESET_EXTENSIONS -> {
                if (packageName.isNullOrBlank()) return MISSING_PACKAGE
                extensions.deleteFor(packageName)
                "OK: ampliaciones de $packageName borradas"
            }
            ACTION_CREATE_OBLIGATION -> {
                val minutes = intent.getIntExtra(EXTRA_MINUTES, 15)
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Cita de prueba"
                val start = clock.instant().plusSeconds(minutes * 60L)
                val id = tasks.save(
                    Task(title = title, type = TaskType.OBLIGATION, energyLevel = EnergyLevel.MEDIUM, startTime = start),
                )
                "OK: obligacion $id '$title' empieza a las $start (en $minutes min)"
            }
            ACTION_CLEAR_OBLIGATIONS -> {
                val all = tasks.observeByType(TaskType.OBLIGATION).first()
                all.forEach { tasks.delete(it) }
                "OK: ${all.size} obligaciones borradas"
            }
            ACTION_EXPIRE_PENDING_LIMITS -> {
                // Adelanta a «ahora» los cambios de límite que esperan a medianoche; el servicio los aplica en su siguiente ciclo.
                val all = pendingChanges.observeAll().first()
                all.forEach { pendingChanges.save(it.copy(appliesAt = clock.instant())) }
                "OK: ${all.size} cambios de limite listos para aplicarse"
            }
            ACTION_SET_REALITY_CHECK -> {
                val threshold = intent.getIntExtra(EXTRA_MINUTES, RealityCheckPolicy.DEFAULT_THRESHOLD_MINUTES)
                val silence = intent.getIntExtra(EXTRA_SILENCE, threshold)
                realityCheck.thresholdMinutes = threshold
                realityCheck.silenceMinutes = silence
                "OK: Reality Check tras $threshold min seguidos, silencio de $silence min"
            }
            ACTION_RESET_REALITY_CHECK -> {
                realityCheck.reset()
                "OK: Reality Check con los tiempos de serie (${realityCheck.thresholdMinutes} min)"
            }
            ACTION_RUN_CLOSURE -> "OK: jornada del ${runClosure.closeToday()} cerrada"
            ACTION_SET_CLOSURE -> {
                val current = closureSettings.state.value.effective
                val updated = current.copy(
                    enabled = intent.getBooleanExtra(EXTRA_ENABLED, current.enabled),
                    closeAt = if (intent.hasExtra(EXTRA_CLOSE_HOUR)) {
                        LocalTime.of(intent.getIntExtra(EXTRA_CLOSE_HOUR, 21), intent.getIntExtra(EXTRA_CLOSE_MINUTE, 0))
                    } else {
                        current.closeAt
                    },
                    liftUntil = if (intent.hasExtra(EXTRA_LIFT_HOUR)) LocalTime.of(intent.getIntExtra(EXTRA_LIFT_HOUR, 6), 0) else current.liftUntil,
                )
                closureSettings.update(DayClosureState(effective = updated))
                scheduleClosure()
                "OK: cierre $updated"
            }
            ACTION_SET_LEISURE -> {
                if (packageName.isNullOrBlank()) return MISSING_PACKAGE
                val existing = limits.getByPackage(packageName) ?: return "ERROR: $packageName no tiene limite"
                val leisure = intent.getBooleanExtra(EXTRA_LEISURE, true)
                limits.save(existing.copy(isLeisure = leisure))
                "OK: $packageName de ocio=$leisure"
            }
            else -> "ERROR: accion desconocida ${intent.action}"
        }
    }

    private companion object {
        const val ACTION_SET_LIMIT = "com.focusdivergent.app.debug.SET_LIMIT"
        const val ACTION_CLEAR_LIMIT = "com.focusdivergent.app.debug.CLEAR_LIMIT"
        const val ACTION_RESET_EXTENSIONS = "com.focusdivergent.app.debug.RESET_EXTENSIONS"
        const val ACTION_CREATE_OBLIGATION = "com.focusdivergent.app.debug.CREATE_OBLIGATION"
        const val ACTION_CLEAR_OBLIGATIONS = "com.focusdivergent.app.debug.CLEAR_OBLIGATIONS"
        const val ACTION_RUN_CLOSURE = "com.focusdivergent.app.debug.RUN_CLOSURE"
        const val ACTION_SET_CLOSURE = "com.focusdivergent.app.debug.SET_CLOSURE"
        const val ACTION_SET_LEISURE = "com.focusdivergent.app.debug.SET_LEISURE"
        const val EXTRA_ENABLED = "enabled"
        const val EXTRA_CLOSE_HOUR = "close_hour"
        const val EXTRA_CLOSE_MINUTE = "close_minute"
        const val EXTRA_LIFT_HOUR = "lift_hour"
        const val EXTRA_LEISURE = "leisure"
        const val ACTION_EXPIRE_PENDING_LIMITS = "com.focusdivergent.app.debug.EXPIRE_PENDING_LIMITS"
        const val EXTRA_EXTENSIONS = "extensions"
        const val ACTION_SET_REALITY_CHECK = "com.focusdivergent.app.debug.SET_REALITY_CHECK"
        const val ACTION_RESET_REALITY_CHECK = "com.focusdivergent.app.debug.RESET_REALITY_CHECK"
        const val EXTRA_SILENCE = "silence"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_MINUTES = "minutes"
        const val EXTRA_STRICT = "strict"
        const val EXTRA_TITLE = "title"
        const val MISSING_PACKAGE = "ERROR: falta --es package <nombre.del.paquete>"
    }
}
