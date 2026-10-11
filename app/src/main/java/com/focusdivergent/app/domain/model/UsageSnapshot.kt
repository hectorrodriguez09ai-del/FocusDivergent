package com.focusdivergent.app.domain.model

/**
 * Lectura del uso de apps desde la medianoche local.
 *
 * @property foregroundPackage app que está en pantalla ahora, o nulo si no hay ninguna (pantalla apagada, por ejemplo).
 * @property usedMillisByPackage tiempo en primer plano de cada app durante el día de hoy.
 * @property foregroundSessionMillis tiempo seguido que lleva [foregroundPackage] en uso. Una salida de hasta
 * `RealityCheckPolicy.GAP_TOLERANCE_MILLIS` no corta la sesión. Es 0 si no hay app en primer plano.
 */
data class UsageSnapshot(
    val foregroundPackage: String?,
    val usedMillisByPackage: Map<String, Long>,
    val foregroundSessionMillis: Long = 0L,
)
