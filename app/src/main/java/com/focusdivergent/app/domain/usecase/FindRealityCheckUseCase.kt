package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.model.RealityCheck
import com.focusdivergent.app.domain.model.UsageSnapshot
import com.focusdivergent.app.domain.realitycheck.RealityCheckSettings
import com.focusdivergent.app.domain.realitycheck.RealityCheckSilences
import com.focusdivergent.app.domain.repository.AppLimitRepository
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Named

/**
 * Decide si hay que preguntar «¿Sigues aquí de forma consciente?» (RF-4.3): la app en pantalla tiene un límite
 * (sin bloqueo estricto), lleva el tiempo de umbral seguido en uso y el usuario no ha respondido hace poco.
 *
 * No comprueba si el límite está agotado: lo evalúa antes [FindBlockedAppUseCase], que tiene prioridad.
 */
class FindRealityCheckUseCase @Inject constructor(
    private val limits: AppLimitRepository,
    private val silences: RealityCheckSilences,
    private val settings: RealityCheckSettings,
    private val clock: Clock,
    @Named("ownPackage") private val ownPackage: String,
) {

    suspend operator fun invoke(snapshot: UsageSnapshot): RealityCheck? {
        val packageName = snapshot.foregroundPackage ?: return null
        if (packageName == ownPackage) return null

        val thresholdMillis = Duration.ofMinutes(settings.thresholdMinutes.toLong()).toMillis()
        if (snapshot.foregroundSessionMillis < thresholdMillis) return null
        if (silences.isSilenced(packageName, clock.instant())) return null

        val limit = limits.getByPackage(packageName) ?: return null
        if (limit.isStrictlyBlocked) return null
        return RealityCheck(packageName, snapshot.foregroundSessionMillis)
    }
}
