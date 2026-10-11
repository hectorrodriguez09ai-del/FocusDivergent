package com.focusdivergent.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

/**
 * Una rutina hecha en un día concreto: una fila por rutina y día.
 *
 * Las rutinas se repiten, así que no les vale el estado único de `tasks`. [day] es el día local como número de
 * días desde 1970 (`LocalDate.toEpochDay()`), para que «hoy» no dependa de la zona horaria en que se guardó.
 * Si se borra la rutina, se borran sus marcas.
 */
@Entity(
    tableName = "routine_completions",
    primaryKeys = ["task_id", "day"],
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["task_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class RoutineCompletionEntity(
    @ColumnInfo(name = "task_id") val taskId: Long,
    val day: Long,
    @ColumnInfo(name = "completed_at") val completedAt: Long,
)
