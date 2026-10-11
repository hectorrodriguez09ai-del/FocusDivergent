package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.model.Task
import com.focusdivergent.app.domain.model.TaskStatus
import com.focusdivergent.app.domain.model.TaskType
import com.focusdivergent.app.domain.repository.RoutineCompletionRepository
import com.focusdivergent.app.domain.repository.TaskRepository
import com.focusdivergent.app.domain.reward.RewardLedger
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Marca o desmarca una actividad como hecha.
 *
 * Una tarea o una Obligación cambian de estado. Una rutina se repite, así que se marca solo para un día: [day],
 * que por defecto es hoy. No se puede marcar una rutina de un día que aún no ha llegado.
 *
 * Marcar suma XP y monedas en el libro de [rewards]; desmarcar retira lo que se había sumado.
 *
 * @return `false` si no se hizo nada (rutina de un día futuro).
 */
class SetTaskDoneUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val completions: RoutineCompletionRepository,
    private val clock: Clock,
    private val rewards: RewardLedger,
) {

    suspend operator fun invoke(task: Task, done: Boolean, day: LocalDate? = null): Boolean {
        val now = clock.instant()
        if (task.type == TaskType.ROUTINE) {
            val today = now.atZone(clock.zone).toLocalDate()
            val target = day ?: today
            if (target > today) return false
            if (done) {
                completions.markCompleted(task.id, target, now)
                rewards.routineDone(task, target)
            } else {
                completions.unmarkCompleted(task.id, target)
                rewards.routineUndone(task, target)
            }
        } else {
            tasks.updateStatus(
                id = task.id,
                status = if (done) TaskStatus.DONE else TaskStatus.PENDING,
                completedAt = if (done) now else null,
            )
            if (done) rewards.taskDone(task) else rewards.taskUndone(task)
        }
        return true
    }
}
