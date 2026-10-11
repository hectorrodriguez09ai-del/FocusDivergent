package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.policy.ReminderPolicy
import com.focusdivergent.app.domain.reminder.ReminderSnoozes
import java.time.Clock
import javax.inject.Inject

/** Pospone [ReminderPolicy.SNOOZE_MINUTES] minutos el aviso de una Obligación, una vez resuelto el reto. */
class SnoozeObligationReminderUseCase @Inject constructor(
    private val snoozes: ReminderSnoozes,
    private val clock: Clock,
) {

    operator fun invoke(taskId: Long) {
        snoozes.snooze(taskId, clock.instant() + ReminderPolicy.SNOOZE)
    }
}
