package com.focusdivergent.app.domain.policy

import java.time.Duration
import java.time.Instant

/**
 * Reglas del aviso preventivo de una Obligación. Constantes por ahora; se harán configurables en la Fase 3.
 */
object ReminderPolicy {

    /** Con cuánta antelación aparece el aviso. */
    const val LEAD_MINUTES = 10L

    /** Lo que se pospone el aviso al resolver el reto matemático. */
    const val SNOOZE_MINUTES = 5L

    val LEAD: Duration = Duration.ofMinutes(LEAD_MINUTES)
    val SNOOZE: Duration = Duration.ofMinutes(SNOOZE_MINUTES)

    /** Instante en que empieza a mostrarse el aviso de una Obligación que arranca en [start]. */
    fun reminderTime(start: Instant): Instant = start - LEAD
}
