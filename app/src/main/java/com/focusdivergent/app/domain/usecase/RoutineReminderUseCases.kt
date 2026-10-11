package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.model.Task
import com.focusdivergent.app.domain.model.TaskType
import com.focusdivergent.app.domain.reminder.RoutineReminderAlarms
import com.focusdivergent.app.domain.reminder.RoutineReminderLog
import com.focusdivergent.app.domain.reminder.RoutineReminderNotifier
import com.focusdivergent.app.domain.reminder.RoutineReminderPolicy
import com.focusdivergent.app.domain.repository.RoutineCompletionRepository
import com.focusdivergent.app.domain.repository.TaskRepository
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Pone la alarma del próximo aviso de rutina, o la quita si no hay ninguna rutina con hora. Hay que llamarlo al
 * cambiar las rutinas, al sonar la alarma y al arrancar.
 */
class ScheduleRoutineRemindersUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val alarms: RoutineReminderAlarms,
    private val clock: Clock,
) {

    suspend operator fun invoke() {
        val routines = tasks.observeByType(TaskType.ROUTINE).first()
        val next = RoutineReminderPolicy.next(routines, clock.instant(), clock.zone)
        if (next == null) alarms.cancel() else alarms.schedule(next)
    }
}

/**
 * Suena la alarma general: avisa de las rutinas que tocan ahora, una sola vez cada una y solo si no están ya hechas
 * hoy. Después deja puesta la alarma del siguiente aviso.
 */
class FireRoutineRemindersUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val completions: RoutineCompletionRepository,
    private val notifier: RoutineReminderNotifier,
    private val log: RoutineReminderLog,
    private val schedule: ScheduleRoutineRemindersUseCase,
    private val clock: Clock,
) {

    suspend operator fun invoke() {
        val now = clock.instant()
        val today = now.atZone(clock.zone).toLocalDate()
        val routines = tasks.observeByType(TaskType.ROUTINE).first()
        val doneToday = completions.observeCompletedOn(today).first()
        RoutineReminderPolicy.due(routines, now, clock.zone)
            .filter { it.id !in doneToday && !log.wasNotified(it.id, today) }
            .forEach { routine ->
                log.markNotified(routine.id, today)
                notifier.show(routine, canSnooze = true)
            }
        schedule()
    }
}

/** Lo que se puede hacer desde el propio aviso: dar la rutina por hecha o pedir que se lo recuerden más tarde. */
class RoutineReminderActionsUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val completions: RoutineCompletionRepository,
    private val setDone: SetTaskDoneUseCase,
    private val notifier: RoutineReminderNotifier,
    private val alarms: RoutineReminderAlarms,
    private val clock: Clock,
) {

    private fun today(): LocalDate = clock.instant().atZone(clock.zone).toLocalDate()

    /** «Hecha»: marca la rutina de hoy (con su XP) y retira el aviso y cualquier «Más tarde» pendiente. */
    suspend fun done(taskId: Long) {
        notifier.dismiss(taskId)
        alarms.cancelSnooze(taskId)
        val task = tasks.getById(taskId) ?: return
        if (task.type == TaskType.ROUTINE) setDone(task, done = true, day = today())
    }

    /** «Más tarde»: quita el aviso y vuelve a recordarlo una sola vez, dentro de [RoutineReminderPolicy.SNOOZE]. */
    fun later(taskId: Long) {
        notifier.dismiss(taskId)
        alarms.scheduleSnooze(taskId, clock.instant() + RoutineReminderPolicy.SNOOZE)
    }

    /** Llega el «Más tarde»: avisa otra vez (ya sin ofrecer más aplazamientos), salvo que ya esté hecha. */
    suspend fun snoozeElapsed(taskId: Long) {
        val task: Task = tasks.getById(taskId) ?: return
        if (task.type != TaskType.ROUTINE) return
        if (taskId in completions.observeCompletedOn(today()).first()) return
        notifier.show(task, canSnooze = false)
    }
}
