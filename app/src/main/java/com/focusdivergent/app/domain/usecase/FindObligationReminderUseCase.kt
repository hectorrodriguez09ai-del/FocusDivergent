package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.model.ObligationReminder
import com.focusdivergent.app.domain.policy.ReminderPolicy
import com.focusdivergent.app.domain.reminder.ReminderSnoozes
import com.focusdivergent.app.domain.repository.TaskRepository
import java.time.Clock
import javax.inject.Inject

/**
 * Decide si ahora toca mostrar el aviso de una Obligación: está pendiente, empieza dentro de
 * [ReminderPolicy.LEAD_MINUTES] minutos o menos y su aviso no está pospuesto. Con varias a la vez, la más próxima.
 */
class FindObligationReminderUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val snoozes: ReminderSnoozes,
    private val clock: Clock,
) {

    suspend operator fun invoke(): ObligationReminder? {
        val now = clock.instant()
        return tasks.getPendingObligationsStartingBetween(now, now + ReminderPolicy.LEAD)
            .firstNotNullOfOrNull { task ->
                val start = task.startTime
                if (start == null || snoozes.isSnoozed(task.id, now)) null
                else ObligationReminder(task.id, task.title, start)
            }
    }
}
