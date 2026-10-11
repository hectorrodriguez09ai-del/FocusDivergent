package com.focusdivergent.app.domain.alarm

import java.time.Instant

/**
 * Puerto hacia el sistema de alarmas: despierta a la app en un instante concreto aunque el servicio de
 * bloqueo no esté en marcha. Hay una sola alarma pendiente a la vez: programar sustituye a la anterior.
 */
interface ObligationAlarmScheduler {

    fun schedule(at: Instant)

    fun cancel()
}
