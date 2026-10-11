package com.focusdivergent.app.domain.policy

/** Constantes del Reality Check. Los dos tiempos son ajustables (ver `RealityCheckSettings`); el valor de serie es este. */
object RealityCheckPolicy {

    /** Minutos seguidos en una app con límite tras los que se pregunta. */
    const val DEFAULT_THRESHOLD_MINUTES = 20

    /** Minutos sin volver a preguntar tras responder «Sí, es a propósito». */
    const val DEFAULT_SILENCE_MINUTES = 20

    /** Una salida de la app de hasta este tiempo (p. ej. mirar una notificación) no reinicia la cuenta. */
    const val GAP_TOLERANCE_MILLIS = 60_000L
}
