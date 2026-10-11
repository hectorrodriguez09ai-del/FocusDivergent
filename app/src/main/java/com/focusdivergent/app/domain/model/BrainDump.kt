package com.focusdivergent.app.domain.model

import java.time.Instant

/** Idea capturada al vuelo (RF-5.1). Espera en la bandeja de entrada hasta que se procesa. */
data class BrainDump(
    val id: Long = 0,
    val content: String,
    val timestamp: Instant,
    val isProcessed: Boolean = false,
)
