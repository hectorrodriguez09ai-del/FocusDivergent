package com.focusdivergent.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.focusdivergent.app.domain.model.EnergyLevel
import com.focusdivergent.app.domain.model.TaskStatus
import com.focusdivergent.app.domain.model.TaskType

/**
 * Tarea, Obligación o Rutina (SRD §3.2).
 *
 * [startTime] y [endTime] son epoch en milisegundos; nulos si la actividad no tiene hora fijada.
 * [recurrenceRule] guarda una regla RRULE (RFC 5545) y solo aplica a las rutinas.
 * [estimatedMinutes] es la duración prevista, opcional. [completedAt] es el momento (epoch ms) en que se marcó
 * como hecha; nulo si no lo está. Las rutinas no lo usan: se marcan por día en `routine_completions`.
 */
@Entity(
    tableName = "tasks",
    indices = [Index("type"), Index("status"), Index("start_time")],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val type: TaskType,
    @ColumnInfo(name = "energy_level") val energyLevel: EnergyLevel,
    val status: TaskStatus = TaskStatus.PENDING,
    @ColumnInfo(name = "start_time") val startTime: Long? = null,
    @ColumnInfo(name = "end_time") val endTime: Long? = null,
    @ColumnInfo(name = "recurrence_rule") val recurrenceRule: String? = null,
    @ColumnInfo(name = "estimated_minutes") val estimatedMinutes: Int? = null,
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
)
