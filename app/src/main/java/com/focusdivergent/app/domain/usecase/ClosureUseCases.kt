package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.closure.ClosureAlarmScheduler
import com.focusdivergent.app.domain.closure.ClosureNotifier
import com.focusdivergent.app.domain.closure.ClosurePolicy
import com.focusdivergent.app.domain.closure.ClosureRecord
import com.focusdivergent.app.domain.closure.ClosureWindow
import com.focusdivergent.app.domain.closure.DayClosureConfig
import com.focusdivergent.app.domain.closure.DayClosureSettings
import com.focusdivergent.app.domain.closure.DayClosureState
import com.focusdivergent.app.domain.closure.DayClosureStore
import com.focusdivergent.app.domain.closure.DaySummary
import com.focusdivergent.app.domain.model.TaskStatus
import com.focusdivergent.app.domain.model.TaskType
import com.focusdivergent.app.domain.repository.AppLimitRepository
import com.focusdivergent.app.domain.repository.BrainDumpRepository
import com.focusdivergent.app.domain.repository.RewardRepository
import com.focusdivergent.app.domain.repository.RoutineCompletionRepository
import com.focusdivergent.app.domain.repository.TaskRepository
import com.focusdivergent.app.domain.repository.UsageRepository
import com.focusdivergent.app.domain.reward.RewardLedger
import com.focusdivergent.app.domain.reward.RewardTotals
import com.focusdivergent.app.domain.time.startOfTomorrow
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val MILLIS_PER_MINUTE = 60_000L

/**
 * Cierra el día (RF-6.2): traslada a mañana lo que quedó sin hacer, anota qué límites se respetaron, deja constancia
 * de que ese día ya está cerrado y avisa de que el resumen está listo. Que las apps de ocio queden libres no se hace
 * aquí: eso lo deduce siempre `ClosureWindow` de la hora, sin depender de que este cierre llegue a ejecutarse.
 *
 * Es seguro llamarlo todo lo que haga falta (la alarma, el servicio de bloqueo, al abrir la app): cada día se cierra
 * una sola vez, y llegar tarde no lo rompe, porque mientras dure la noche sigue tocando cerrar el día que no se cerró.
 */
class RunDayClosureUseCase @Inject constructor(
    private val settings: DayClosureSettings,
    private val store: DayClosureStore,
    private val tasks: TaskRepository,
    private val limits: AppLimitRepository,
    private val usage: UsageRepository,
    private val brainDump: BrainDumpRepository,
    private val rewards: RewardLedger,
    private val notifier: ClosureNotifier,
    private val clock: Clock,
) {

    /** @return el día que se cerró, o nulo si no tocaba cerrar nada. */
    suspend operator fun invoke(): LocalDate? = lock.withLock {
        val now = clock.instant()
        val config = settings.state.value.configAt(now)
        val day = ClosureWindow.dueDay(config, now, clock.zone) ?: return null
        val last = store.record.value?.day
        if (last != null && last >= day) return null
        close(day)
        day
    }

    /** Cierra el día de hoy aunque no sea la hora ni esté ya cerrado. Es lo que usan las herramientas de prueba. */
    suspend fun closeToday(): LocalDate = lock.withLock {
        val day = clock.instant().atZone(clock.zone).toLocalDate()
        close(day)
        day
    }

    private suspend fun close(day: LocalDate) {
        val moved = moveUnfinished(day)
        val respected = settleLimits(day)
        store.save(ClosureRecord(day, moved, respected))
        notifier.show(day, ideasWaiting = brainDump.observeUnprocessed().first().size)
    }

    /**
     * Pasa a mañana las tareas **con fecha de [day]** que no se terminaron, a la misma hora. Las que no tienen fecha, las
     * Obligaciones y las Rutinas no se tocan. Sin avisos ni marcas: la tarea simplemente aparece mañana.
     */
    private suspend fun moveUnfinished(day: LocalDate): Int {
        val zone = clock.zone
        val due = tasks.observeAll().first().filter {
            it.type == TaskType.TASK && it.status != TaskStatus.DONE && it.startTime?.atZone(zone)?.toLocalDate() == day
        }
        due.forEach { task ->
            tasks.save(
                task.copy(
                    startTime = task.startTime?.atZone(zone)?.plusDays(1)?.toInstant(),
                    endTime = task.endTime?.atZone(zone)?.plusDays(1)?.toInstant(),
                ),
            )
        }
        return due.size
    }

    /**
     * Da XP por cada app cuyo límite no se llegó a gastar. Se mide aquí, al cierre, y solo para el día en curso porque
     * el uso que se lee es el de hoy. Si no se puede leer el uso, no se da ni se quita nada.
     */
    private suspend fun settleLimits(day: LocalDate): Int {
        if (day != clock.instant().atZone(clock.zone).toLocalDate()) return 0
        return try {
            val used = usage.readToday().usedMillisByPackage
            // Sin el permiso de uso la lectura sale vacía: no se puede saber si se respetó nada.
            if (used.isEmpty()) return 0
            val respected = limits.observeAll().first().filter {
                !it.isStrictlyBlocked && (used[it.packageName] ?: 0L) <= it.dailyMinutesAllowed * MILLIS_PER_MINUTE
            }
            respected.forEach { rewards.limitRespected(it.packageName, day) }
            respected.size
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            0
        }
    }

    private companion object {
        // Una sola ejecución a la vez en todo el proceso: la alarma y el servicio podrían coincidir.
        val lock = Mutex()
    }
}

/**
 * El resumen de [day]: lo que se hizo y lo que se ganó, y qué pasó al cerrar. Solo lo hecho; lo que faltó no sale
 * (RNF-3). Se calcula en vivo, así que sigue siendo cierto si se hace algo más después del cierre.
 */
class ObserveDaySummaryUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val completions: RoutineCompletionRepository,
    private val rewards: RewardRepository,
    private val brainDump: BrainDumpRepository,
    private val store: DayClosureStore,
    private val clock: Clock,
) {

    operator fun invoke(day: LocalDate): Flow<DaySummary> = combine(
        tasks.observeAll(),
        completions.observeCompletedOn(day),
        rewards.observeDailyTotals(),
        brainDump.observeUnprocessed(),
        store.record,
    ) { all, routineIds, totals, ideas, record ->
        val zone = clock.zone
        val doneTasks = all
            .filter { it.type != TaskType.ROUTINE && it.status == TaskStatus.DONE && it.completedAt?.atZone(zone)?.toLocalDate() == day }
            .sortedBy { it.completedAt }
        val doneRoutines = all.filter { it.type == TaskType.ROUTINE && it.id in routineIds }
        val closed = record?.takeIf { it.day == day }
        DaySummary(
            day = day,
            doneTitles = (doneTasks + doneRoutines).map { it.title },
            tasksDone = doneTasks.size,
            routinesDone = doneRoutines.size,
            focusMinutes = RewardTotals.minutesOn(totals, day),
            xp = RewardTotals.xpOn(totals, day),
            coins = RewardTotals.coinsOn(totals, day),
            moved = closed?.moved ?: 0,
            respectedLimits = closed?.respectedLimits ?: 0,
            ideasWaiting = ideas.size,
        )
    }
}

/**
 * Pone la alarma de la próxima hora de cierre. Los cambios de configuración solo entran en vigor a medianoche, así
 * que se mira cómo estará la configuración cada uno de los próximos días.
 */
class ScheduleDayClosureUseCase @Inject constructor(
    private val settings: DayClosureSettings,
    private val alarms: ClosureAlarmScheduler,
    private val clock: Clock,
) {

    operator fun invoke() {
        val now = clock.instant()
        val zone = clock.zone
        val today = now.atZone(zone).toLocalDate()
        val state = settings.state.value
        for (offset in 0L..2L) {
            val date = today.plusDays(offset)
            val config = state.configAt(date.atStartOfDay(zone).toInstant())
            if (!config.enabled) continue
            val at = date.atTime(config.closeAt).atZone(zone).toInstant()
            if (at > now) {
                alarms.schedule(at)
                return
            }
        }
        alarms.cancel()
    }
}

/**
 * Cambia la configuración del Cierre con la regla de los límites: lo que endurece rige ya y lo que da más margen a las
 * apps de ocio espera a medianoche. Después deja la alarma al día.
 */
class ChangeClosureSettingsUseCase @Inject constructor(
    private val settings: DayClosureSettings,
    private val schedule: ScheduleDayClosureUseCase,
    private val clock: Clock,
) {

    operator fun invoke(desired: DayClosureConfig) {
        val current = settings.state.value.configAt(clock.instant())
        settings.update(ClosurePolicy.plan(current, desired, clock.startOfTomorrow()))
        schedule()
    }

    /** Descarta el cambio que esperaba a medianoche: sigue como estaba. */
    fun cancelPending() {
        settings.update(DayClosureState(effective = settings.state.value.configAt(clock.instant())))
        schedule()
    }
}
