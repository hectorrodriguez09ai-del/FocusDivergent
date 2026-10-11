package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.model.TimeExtension
import com.focusdivergent.app.domain.policy.ExtensionPolicy
import com.focusdivergent.app.domain.repository.AppLimitRepository
import com.focusdivergent.app.domain.repository.TimeExtensionRepository
import com.focusdivergent.app.domain.repository.UsageRepository
import com.focusdivergent.app.domain.time.startOfToday
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Resultado de intentar conceder tiempo extra tras resolver el reto matemático. */
sealed interface GrantResult {
    data class Granted(val extensionsLeft: Int) : GrantResult
    data object NoLimit : GrantResult
    data object StrictlyBlocked : GrantResult
    data object NoMoreExtensions : GrantResult
}

/**
 * Concede [ExtensionPolicy.MINUTES_PER_EXTENSION] minutos de uso a una app, respetando el tope diario (RF-1.3).
 *
 * Es el único sitio que escribe ampliaciones: el reto matemático ya se ha resuelto antes de llegar aquí, pero
 * el tope y el bloqueo estricto se vuelven a comprobar por si la pantalla estaba desfasada.
 */
@Singleton
class GrantExtraTimeUseCase @Inject constructor(
    private val limits: AppLimitRepository,
    private val extensions: TimeExtensionRepository,
    private val usage: UsageRepository,
    private val clock: Clock,
) {

    // Comprobar el tope y escribir debe ser atómico: dos toques seguidos no pueden colar una cuarta ampliación.
    private val lock = Mutex()

    suspend operator fun invoke(packageName: String): GrantResult = lock.withLock {
        val limit = limits.getByPackage(packageName) ?: return GrantResult.NoLimit
        if (limit.isStrictlyBlocked) return GrantResult.StrictlyBlocked

        val today = extensions.getSince(packageName, clock.startOfToday())
        val remaining = ExtensionPolicy.remaining(limit, today.size)
        if (remaining == 0) return GrantResult.NoMoreExtensions

        val used = usage.readToday().usedMillisByPackage[packageName] ?: 0L
        extensions.add(
            TimeExtension(
                packageName = packageName,
                grantedAt = clock.instant(),
                minutes = ExtensionPolicy.minutesToGrant(limit, today.sumOf { it.minutes }, used),
            ),
        )
        GrantResult.Granted(extensionsLeft = remaining - 1)
    }
}
