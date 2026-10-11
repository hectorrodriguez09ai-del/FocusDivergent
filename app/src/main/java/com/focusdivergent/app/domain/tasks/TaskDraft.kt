package com.focusdivergent.app.domain.tasks

import com.focusdivergent.app.domain.model.EnergyLevel
import com.focusdivergent.app.domain.model.Task
import com.focusdivergent.app.domain.model.TaskType
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Una actividad tal como se edita: con día y hora por separado, que es como piensa el usuario, en vez del
 * instante único que se guarda. [original] es la actividad que se está editando, o nulo si es nueva.
 *
 * Una tarea puede tener solo día (sin hora); se guarda a medianoche y esa hora no se muestra. Una rutina no tiene
 * día (lo marca su [recurrence]), solo una hora opcional. Una Obligación necesita las dos cosas.
 */
data class TaskDraft(
    val original: Task? = null,
    val title: String = "",
    val type: TaskType = TaskType.TASK,
    val energy: EnergyLevel = EnergyLevel.MEDIUM,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val estimatedMinutes: Int? = null,
    val recurrence: Recurrence = Recurrence.Daily,
) {
    val isNew: Boolean get() = original == null

    companion object {
        fun from(task: Task, zone: ZoneId): TaskDraft {
            val start = task.startTime?.atZone(zone)
            val time = start?.toLocalTime()
            return TaskDraft(
                original = task,
                title = task.title,
                type = task.type,
                energy = task.energyLevel,
                date = if (task.type == TaskType.ROUTINE) null else start?.toLocalDate(),
                // La medianoche exacta significa «sin hora», salvo en una Obligación, que siempre la tiene.
                time = time?.takeUnless { it == LocalTime.MIDNIGHT && task.type != TaskType.OBLIGATION },
                estimatedMinutes = task.estimatedMinutes,
                recurrence = Recurrence.parse(task.recurrenceRule),
            )
        }
    }
}

/** Lo que impide guardar un [TaskDraft]. Se enseña junto al campo, en tono neutro (RNF-3). */
enum class TaskProblem {
    TITLE_BLANK,

    /** Una Obligación lanza un aviso antes de su hora, así que necesita día y hora. */
    OBLIGATION_NEEDS_DATE_AND_TIME,
}
