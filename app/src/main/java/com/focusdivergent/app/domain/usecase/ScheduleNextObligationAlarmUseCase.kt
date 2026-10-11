package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.alarm.ObligationAlarmScheduler
import com.focusdivergent.app.domain.policy.ReminderPolicy
import com.focusdivergent.app.domain.repository.TaskRepository
import java.time.Clock
import javax.inject.Inject

/**
 * Programa la alarma que despierta a la app cuando toca el siguiente aviso, o la cancela si no hay ninguno.
 *
 * Solo cuenta las Obligaciones cuyo aviso aún no ha llegado: las que ya están dentro de su margen las
 * cubre el bucle del servicio de bloqueo. Hay que llamarlo al cambiar las tareas y al sonar la alarma.
 */
class ScheduleNextObligationAlarmUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val scheduler: ObligationAlarmScheduler,
    private val clock: Clock,
) {

    suspend operator fun invoke() {
        val next = tasks.getNextPendingObligationStartingAfter(clock.instant() + ReminderPolicy.LEAD)
        val start = next?.startTime
        if (start == null) scheduler.cancel() else scheduler.schedule(ReminderPolicy.reminderTime(start))
    }
}
