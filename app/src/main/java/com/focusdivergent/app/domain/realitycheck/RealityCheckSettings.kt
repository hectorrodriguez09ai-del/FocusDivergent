package com.focusdivergent.app.domain.realitycheck

/** Tiempos del Reality Check. Hoy salen de valores de serie; la Fase 3 los expondrá al usuario. */
interface RealityCheckSettings {

    /** Minutos seguidos en una app con límite tras los que se pregunta. */
    val thresholdMinutes: Int

    /** Minutos sin volver a preguntar tras responder que el uso es a propósito. */
    val silenceMinutes: Int
}
