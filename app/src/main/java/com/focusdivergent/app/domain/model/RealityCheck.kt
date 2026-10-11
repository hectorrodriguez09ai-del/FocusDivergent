package com.focusdivergent.app.domain.model

/**
 * Pregunta de Reality Check (RF-4.3): [packageName] lleva [continuousMillis] seguidos en pantalla y conviene
 * confirmar que el usuario sigue ahí a propósito.
 */
data class RealityCheck(
    val packageName: String,
    val continuousMillis: Long,
) : Interruption {

    override val key: String get() = "reality:$packageName"
}
