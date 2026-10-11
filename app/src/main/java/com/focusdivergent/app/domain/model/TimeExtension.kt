package com.focusdivergent.app.domain.model

import java.time.Instant

/**
 * Una ampliación ganada para [packageName] resolviendo el reto matemático (RF-1.3).
 *
 * [minutes] es lo que se suma al límite diario: los 15 ganados más el tiempo que la app ya había consumido
 * pasado el límite en el momento de resolver el reto (ver `ExtensionPolicy.minutesToGrant`).
 */
data class TimeExtension(
    val id: Long = 0,
    val packageName: String,
    val grantedAt: Instant,
    val minutes: Int,
)
