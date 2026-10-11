package com.focusdivergent.app.domain.reminder

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Obligaciones cuyo aviso está pospuesto, y hasta cuándo.
 *
 * Solo vive en memoria: si el proceso muere el aviso puede repetirse, que es un fallo hacia más fricción y
 * no hacia menos. Una entrada por Obligación pospuesta; son tan pocas que no hace falta limpiarlas.
 */
@Singleton
class ReminderSnoozes @Inject constructor() {

    private val until = ConcurrentHashMap<Long, Instant>()

    fun snooze(taskId: Long, until: Instant) {
        this.until[taskId] = until
    }

    fun isSnoozed(taskId: Long, now: Instant): Boolean = until[taskId]?.let { now < it } ?: false
}
