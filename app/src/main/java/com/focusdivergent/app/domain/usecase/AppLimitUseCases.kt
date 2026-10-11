package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.model.AppLimit
import com.focusdivergent.app.domain.policy.LimitChangePlan
import com.focusdivergent.app.domain.policy.LimitChangePolicy
import com.focusdivergent.app.domain.repository.AppLimitRepository
import com.focusdivergent.app.domain.repository.PendingLimitChangeRepository
import com.focusdivergent.app.domain.time.startOfTomorrow
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

/** Qué ha pasado con un cambio de límite. */
sealed interface SaveLimitResult {
    /** El límite pedido ya era el actual. */
    data object Unchanged : SaveLimitResult

    /** Entró en vigor al momento. */
    data object Applied : SaveLimitResult

    /** El límite se quitó en el acto. */
    data object Removed : SaveLimitResult

    /**
     * Algo relaja, así que espera a [appliesAt]. [partlyApplied] es `true` si la parte que endurece ya rige.
     */
    data class Scheduled(val appliesAt: Instant, val partlyApplied: Boolean) : SaveLimitResult
}

/**
 * Crea, cambia o quita el límite de una app respetando la regla de [LimitChangePolicy]: endurecer es inmediato,
 * relajar espera a medianoche y quitarlo es inmediato (tras el reto matemático que exige la pantalla).
 *
 * Es el único camino de la interfaz hacia `app_limits`. Pedir un cambio sustituye siempre al pendiente anterior:
 * lo último que decide el usuario es lo que cuenta.
 */
class SaveAppLimitUseCase @Inject constructor(
    private val limits: AppLimitRepository,
    private val pending: PendingLimitChangeRepository,
    private val clock: Clock,
) {

    /** @param desired cómo debe quedar el límite, o nulo para quitarlo en el acto. */
    suspend operator fun invoke(packageName: String, desired: AppLimit?): SaveLimitResult {
        val current = limits.getByPackage(packageName)
        val normalized = desired?.normalized(packageName)

        return when (val plan = LimitChangePolicy.plan(packageName, current, normalized, clock.startOfTomorrow())) {
            LimitChangePlan.NoChange -> {
                // Pedir lo que ya hay anula cualquier cambio que estuviera esperando.
                pending.delete(packageName)
                SaveLimitResult.Unchanged
            }
            is LimitChangePlan.ApplyNow -> {
                limits.save(plan.limit)
                pending.delete(packageName)
                SaveLimitResult.Applied
            }
            LimitChangePlan.Remove -> {
                // Se lleva por delante también cualquier cambio que esperara a medianoche.
                current?.let { limits.delete(it) }
                pending.delete(packageName)
                SaveLimitResult.Removed
            }
            is LimitChangePlan.Schedule -> {
                plan.applyNow?.let { limits.save(it) }
                pending.save(plan.pending)
                SaveLimitResult.Scheduled(plan.pending.appliesAt, partlyApplied = plan.applyNow != null)
            }
        }
    }

    // Los minutos nunca bajan de MIN_MINUTES ni suben de MAX_MINUTES, y las ampliaciones no son negativas.
    private fun AppLimit.normalized(packageName: String) = copy(
        packageName = packageName,
        dailyMinutesAllowed = dailyMinutesAllowed.coerceIn(MIN_MINUTES, MAX_MINUTES),
        extensionsPerDay = extensionsPerDay.coerceIn(0, AppLimit.DEFAULT_EXTENSIONS_PER_DAY),
    )

    companion object {
        const val MIN_MINUTES = 5
        const val MAX_MINUTES = 480
    }
}

/** El usuario se arrepiente de un cambio que esperaba a medianoche: el límite actual sigue como estaba. */
class CancelPendingLimitChangeUseCase @Inject constructor(
    private val pending: PendingLimitChangeRepository,
) {
    suspend operator fun invoke(packageName: String) = pending.delete(packageName)
}

/**
 * Hace efectivos los cambios de límite cuya hora ya ha llegado. Se llama en cada ciclo del servicio de bloqueo
 * y al abrir la pantalla de límites: es una consulta mínima y así el cambio entra en vigor a medianoche aunque
 * nadie abra la app.
 *
 * @return cuántos cambios se aplicaron.
 */
class ApplyDueLimitChangesUseCase @Inject constructor(
    private val limits: AppLimitRepository,
    private val pending: PendingLimitChangeRepository,
    private val clock: Clock,
) {

    suspend operator fun invoke(): Int {
        // Quitar un límite ya no espera a medianoche, pero puede quedar algún cambio de ese tipo guardado por una
        // versión anterior de la app: se sigue aplicando a su hora.
        val due = pending.getDue(clock.instant())
        for (change in due) {
            val newLimit = change.newLimit
            if (newLimit != null) {
                limits.save(newLimit)
            } else {
                limits.getByPackage(change.packageName)?.let { limits.delete(it) }
            }
            pending.delete(change.packageName)
        }
        return due.size
    }
}
