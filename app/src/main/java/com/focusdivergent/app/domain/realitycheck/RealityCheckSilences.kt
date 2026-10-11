package com.focusdivergent.app.domain.realitycheck

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Apps a las que el usuario respondió «Sí, es a propósito», y hasta cuándo no se les vuelve a preguntar.
 *
 * Solo vive en memoria: si el proceso muere la pregunta puede repetirse, que es un fallo hacia más
 * fricción y no hacia menos.
 */
@Singleton
class RealityCheckSilences @Inject constructor() {

    private val until = ConcurrentHashMap<String, Instant>()

    fun silence(packageName: String, until: Instant) {
        this.until[packageName] = until
    }

    fun isSilenced(packageName: String, now: Instant): Boolean = until[packageName]?.let { now < it } ?: false
}
