package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.model.Task
import com.focusdivergent.app.domain.model.TaskStatus
import com.focusdivergent.app.domain.model.TaskType
import com.focusdivergent.app.domain.repository.TaskRepository
import javax.inject.Inject

/**
 * Empieza una actividad: una tarea pasa a «en marcha», para que «Hoy» la tenga delante hasta que se acabe.
 *
 * Una rutina no tiene estado propio (se marca por día) y una Obligación tiene su hora, así que con ellas no hace
 * nada. No hay estado «atrasada»: de «en marcha» solo se sale a «hecha» o de vuelta a «pendiente» (RNF-3).
 */
class StartTaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
) {

    /** @return `true` si la actividad quedó en marcha. */
    suspend operator fun invoke(task: Task): Boolean {
        if (task.type != TaskType.TASK || task.status == TaskStatus.DONE) return false
        tasks.updateStatus(task.id, TaskStatus.IN_PROGRESS, completedAt = null)
        return true
    }
}
