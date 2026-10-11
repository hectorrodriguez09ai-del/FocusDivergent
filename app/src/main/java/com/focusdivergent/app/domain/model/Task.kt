package com.focusdivergent.app.domain.model

import java.time.Instant

/**
 * Tarea, Obligación o Rutina (SRD §3.2).
 *
 * [id] es 0 mientras la actividad no se ha guardado. [recurrenceRule] es una regla RRULE
 * (RFC 5545) y solo aplica a las rutinas (ver `Recurrence`). [estimatedMinutes] es la duración prevista,
 * opcional. [completedAt] es cuándo se marcó como hecha; las rutinas no lo usan, se marcan por día.
 */
data class Task(
    val id: Long = 0,
    val title: String,
    val type: TaskType,
    val energyLevel: EnergyLevel,
    val status: TaskStatus = TaskStatus.PENDING,
    val startTime: Instant? = null,
    val endTime: Instant? = null,
    val recurrenceRule: String? = null,
    val estimatedMinutes: Int? = null,
    val completedAt: Instant? = null,
)
