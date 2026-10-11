package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.model.Task
import com.focusdivergent.app.domain.model.TaskType
import com.focusdivergent.app.domain.repository.TaskRepository
import com.focusdivergent.app.domain.tasks.TaskDraft
import com.focusdivergent.app.domain.tasks.TaskProblem
import java.time.Clock
import java.time.LocalTime
import javax.inject.Inject

sealed interface SaveTaskResult {
    data class Saved(val id: Long) : SaveTaskResult
    data class Invalid(val problems: Set<TaskProblem>) : SaveTaskResult
}

/**
 * Valida un [TaskDraft] y lo guarda como [Task]. Solo el título es obligatorio; una Obligación necesita además
 * día y hora, porque sin ellos no puede avisar.
 *
 * Al editar conserva lo que el editor no toca (estado, hora de fin, cuándo se completó).
 */
class SaveTaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val clock: Clock,
) {

    suspend operator fun invoke(draft: TaskDraft): SaveTaskResult {
        val problems = validate(draft)
        if (problems.isNotEmpty()) return SaveTaskResult.Invalid(problems)
        return SaveTaskResult.Saved(tasks.save(draft.toTask()))
    }

    /** Lo que falta para poder guardar; vacío si está todo. */
    fun validate(draft: TaskDraft): Set<TaskProblem> = buildSet {
        if (draft.title.isBlank()) add(TaskProblem.TITLE_BLANK)
        if (draft.type == TaskType.OBLIGATION && (draft.date == null || draft.time == null)) {
            add(TaskProblem.OBLIGATION_NEEDS_DATE_AND_TIME)
        }
    }

    private fun TaskDraft.toTask(): Task {
        val zone = clock.zone
        val today = clock.instant().atZone(zone).toLocalDate()
        val start = when (type) {
            // Una rutina no tiene día propio: solo interesa su hora, si la tiene.
            TaskType.ROUTINE -> time?.let { today.atTime(it) }
            // Con hora pero sin día se entiende que es hoy; con día pero sin hora se guarda a medianoche.
            else -> when {
                date != null -> date.atTime(time ?: LocalTime.MIDNIGHT)
                time != null -> today.atTime(time)
                else -> null
            }
        }?.atZone(zone)?.toInstant()

        val base = original ?: Task(title = "", type = type, energyLevel = energy)
        return base.copy(
            title = title.trim(),
            type = type,
            energyLevel = energy,
            startTime = start,
            estimatedMinutes = estimatedMinutes?.takeIf { it > 0 },
            recurrenceRule = if (type == TaskType.ROUTINE) recurrence.toRule() else null,
        )
    }
}
