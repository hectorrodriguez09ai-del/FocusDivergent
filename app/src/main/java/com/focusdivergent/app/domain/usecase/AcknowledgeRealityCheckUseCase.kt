package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.realitycheck.RealityCheckSettings
import com.focusdivergent.app.domain.realitycheck.RealityCheckSilences
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

/** El usuario confirmó que sigue en [packageName] a propósito: no se le vuelve a preguntar durante un rato. */
class AcknowledgeRealityCheckUseCase @Inject constructor(
    private val silences: RealityCheckSilences,
    private val settings: RealityCheckSettings,
    private val clock: Clock,
) {

    operator fun invoke(packageName: String) {
        silences.silence(packageName, clock.instant() + Duration.ofMinutes(settings.silenceMinutes.toLong()))
    }
}
