package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.launch.FocusLaunchStore
import com.focusdivergent.app.domain.model.FocusLaunch
import com.focusdivergent.app.domain.model.FocusLaunchPrompt
import com.focusdivergent.app.domain.model.FocusLaunchSession
import com.focusdivergent.app.domain.model.Interruption
import com.focusdivergent.app.domain.model.Task
import com.focusdivergent.app.domain.model.TaskStatus
import com.focusdivergent.app.domain.model.TaskType
import com.focusdivergent.app.domain.repository.TaskRepository
import com.focusdivergent.app.domain.reward.RewardLedger
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

/**
 * Decide si hay una Lanzadera que mostrar: el bloqueo mientras dura, y después la pregunta de si se sigue, que
 * permanece hasta que se contesta.
 */
class FindFocusLaunchUseCase @Inject constructor(
    private val store: FocusLaunchStore,
    private val clock: Clock,
) {

    operator fun invoke(): Interruption? {
        val session = store.current() ?: return null
        return if (clock.instant() < session.endsAt) {
            FocusLaunch(session.taskTitle, session.startedAt, session.endsAt)
        } else {
            FocusLaunchPrompt(session.taskId, session.taskTitle, session.startedAt)
        }
    }
}

/**
 * Pone en marcha una Lanzadera de [FocusLaunchSession.DURATION_SECONDS] segundos.
 *
 * No se puede empezar otra mientras haya una en curso o sin contestar: una a la vez, para que no se encadenen
 * sin querer y el bloqueo no se alargue más de lo que el usuario eligió.
 */
class StartFocusLaunchUseCase @Inject constructor(
    private val store: FocusLaunchStore,
    private val clock: Clock,
) {

    /** @param task la tarea que se va a empezar, o nulo para empezar «lo que sea». @return `false` si ya había una. */
    operator fun invoke(task: Task?): Boolean {
        if (store.current() != null) return false
        val now = clock.instant()
        store.save(
            FocusLaunchSession(
                taskId = task?.id,
                taskTitle = task?.title,
                startedAt = now,
                endsAt = now + Duration.ofSeconds(FocusLaunchSession.DURATION_SECONDS),
            ),
        )
        return true
    }
}

/**
 * Cierra la Lanzadera. Las dos salidas son legítimas y ninguna deja huella negativa (RNF-3): seguir deja la tarea
 * en marcha; dejarlo por ahora no registra nada.
 */
class FinishFocusLaunchUseCase @Inject constructor(
    private val store: FocusLaunchStore,
    private val tasks: TaskRepository,
    private val rewards: RewardLedger,
) {

    /**
     * «Seguir»: la tarea elegida pasa a estar en marcha (si es una tarea), la sesión se cierra y haber empezado
     * y seguido suma un poco de XP.
     */
    suspend fun continueWork(taskId: Long?) {
        val session = store.current()
        store.clear()
        session?.let { rewards.launchContinued(it) }
        val task = taskId?.let { tasks.getById(it) } ?: return
        if (task.type == TaskType.TASK && task.status == TaskStatus.PENDING) {
            tasks.updateStatus(task.id, TaskStatus.IN_PROGRESS, completedAt = null)
        }
    }

    /** «Lo dejo por ahora»: sin consecuencias. */
    fun stop() = store.clear()
}
