package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.model.Task
import com.focusdivergent.app.domain.model.TaskType
import com.focusdivergent.app.domain.repository.TaskRepository
import com.focusdivergent.app.domain.reward.RewardLedger
import com.focusdivergent.app.domain.tasks.TaskTrash
import javax.inject.Inject

/**
 * Borra una actividad y la deja en la papelera unos segundos, por si se quiere deshacer.
 *
 * Borrar no quita XP (solo deshacer lo hace), pero sí se lleva las marcas diarias de una rutina; por eso se guarda
 * antes la mejor racha, que no debe bajar.
 */
class DeleteTaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val trash: TaskTrash,
    private val rewards: RewardLedger,
) {

    suspend operator fun invoke(task: Task) {
        if (task.type == TaskType.ROUTINE) rewards.refreshBestStreak()
        tasks.delete(task)
        trash.put(task)
    }
}

/**
 * Deshace el último borrado: la actividad vuelve con su mismo id. Las marcas diarias de una rutina no se
 * recuperan (se borraron con ella).
 */
class RestoreDeletedTaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val trash: TaskTrash,
) {

    suspend operator fun invoke() {
        val task = trash.lastDeleted.value ?: return
        tasks.save(task)
        trash.clear(task)
    }
}
