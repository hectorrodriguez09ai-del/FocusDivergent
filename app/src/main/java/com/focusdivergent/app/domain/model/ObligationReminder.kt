package com.focusdivergent.app.domain.model

import java.time.Instant

/**
 * Aviso preventivo de una Obligación (RF-1.2): a falta de pocos minutos, una pantalla completa recuerda al
 * usuario que es hora de prepararse.
 */
data class ObligationReminder(
    val taskId: Long,
    val title: String,
    val startTime: Instant,
) : Interruption {

    override val key: String get() = "obligation:$taskId"
}
