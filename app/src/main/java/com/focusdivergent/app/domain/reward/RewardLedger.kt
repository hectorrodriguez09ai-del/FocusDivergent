package com.focusdivergent.app.domain.reward

import com.focusdivergent.app.domain.model.FocusLaunchSession
import com.focusdivergent.app.domain.model.Task
import com.focusdivergent.app.domain.repository.RewardRepository
import com.focusdivergent.app.domain.repository.RoutineCompletionRepository
import com.focusdivergent.app.domain.repository.UserStatsRepository
import com.focusdivergent.app.domain.timer.TimerSession
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Lo que se anota en el libro de XP cuando el usuario hace algo. Los casos de uso que cambian el estado de una
 * actividad lo llaman; aquí está la regla de cada premio y de cada «deshacer».
 *
 * Deshacer (desmarcar una tarea o una rutina) borra el movimiento que había causado, así que marcar y desmarcar
 * no se puede usar para inflar el XP: el total sale siempre de lo que hay anotado.
 */
class RewardLedger @Inject constructor(
    private val rewards: RewardRepository,
    private val completions: RoutineCompletionRepository,
    private val stats: UserStatsRepository,
    private val clock: Clock,
) {

    private fun today(): LocalDate = clock.instant().atZone(clock.zone).toLocalDate()

    suspend fun taskDone(task: Task) = rewards.record(RewardRules.taskDone(task, today()))

    /** Retira el XP de la tarea, con independencia del día en que se marcó. */
    suspend fun taskUndone(task: Task) = rewards.retract(RewardCause.TASK_DONE, task.id)

    suspend fun stepDone(stepId: Long) = rewards.record(RewardRules.stepDone(stepId, today()))

    /** Retira el XP del paso, con independencia del día en que se marcó. */
    suspend fun stepUndone(stepId: Long) = rewards.retract(RewardCause.STEP_DONE, stepId)

    suspend fun routineDone(task: Task, day: LocalDate) {
        rewards.record(RewardRules.routineDone(task, day))
        refreshBestStreak()
    }

    suspend fun routineUndone(task: Task, day: LocalDate) =
        rewards.retract(RewardCause.ROUTINE_DONE, task.id, day)

    /** Anota los bloques de enfoque terminados de [session]. Si no hay ninguno, no anota nada. */
    suspend fun focusSession(session: TimerSession) {
        RewardRules.focusSession(session, clock.instant(), clock.zone)?.let { rewards.record(it) }
    }

    suspend fun launchContinued(session: FocusLaunchSession) =
        rewards.record(RewardRules.launchContinued(session, today()))

    /** Anota que se respetó el límite de [packageName] el día [day]. */
    suspend fun limitRespected(packageName: String, day: LocalDate) =
        rewards.record(RewardRules.limitRespected(packageName, day))

    /**
     * Guarda la mejor racha si la actual la supera. Hay que llamarlo antes de que desaparezcan las marcas de una
     * Rutina (al borrarla), porque la mejor racha no debe bajar.
     */
    suspend fun refreshBestStreak() {
        val streak = StreakCalculator.compute(completions.getCompletedDays(), today())
        stats.raiseBestStreak(streak.best)
    }
}
