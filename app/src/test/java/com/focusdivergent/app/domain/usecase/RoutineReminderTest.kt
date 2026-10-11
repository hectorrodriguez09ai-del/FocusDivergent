package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.model.Task
import com.focusdivergent.app.domain.model.TaskType
import com.focusdivergent.app.domain.reminder.RoutineReminderAlarms
import com.focusdivergent.app.domain.reminder.RoutineReminderLog
import com.focusdivergent.app.domain.reminder.RoutineReminderNotifier
import com.focusdivergent.app.domain.reminder.RoutineReminderPolicy
import com.focusdivergent.app.domain.reward.RewardCause
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeRoutineAlarms : RoutineReminderAlarms {
    var general: Instant? = null
    val snoozes = mutableMapOf<Long, Instant>()

    override fun schedule(at: Instant) {
        general = at
    }

    override fun cancel() {
        general = null
    }

    override fun scheduleSnooze(taskId: Long, at: Instant) {
        snoozes[taskId] = at
    }

    override fun cancelSnooze(taskId: Long) {
        snoozes.remove(taskId)
    }
}

class FakeRoutineNotifier : RoutineReminderNotifier {
    data class Shown(val task: Task, val canSnooze: Boolean)

    val shown = mutableListOf<Shown>()
    val dismissed = mutableListOf<Long>()

    override fun show(task: Task, canSnooze: Boolean) {
        shown += Shown(task, canSnooze)
    }

    override fun dismiss(taskId: Long) {
        dismissed += taskId
    }
}

class FakeRoutineLog : RoutineReminderLog {
    private val notified = mutableSetOf<Pair<Long, LocalDate>>()

    override fun wasNotified(taskId: Long, day: LocalDate) = (taskId to day) in notified

    override fun markNotified(taskId: Long, day: LocalDate) {
        notified.removeAll { it.second != day }
        notified += taskId to day
    }
}

/** Los avisos amables de Rutinas: cuándo avisan, que no se repitan y que «Hecha» y «Más tarde» hagan lo suyo. */
class RoutineReminderTest {

    private val zone = ZoneOffset.UTC

    /** Rutina con hora [minutes] minutos después de las 12:00 de [TEST_NOW]. */
    private fun routine(id: Long, minutes: Long? = 30, rule: String? = null) =
        task(id, type = TaskType.ROUTINE, startsInMinutes = minutes, recurrenceRule = rule)

    private fun at(hour: Int, minute: Int, day: Int = 10) = Instant.parse("2026-10-%02dT%02d:%02d:00Z".format(day, hour, minute))

    // ---- Política

    @Test
    fun `solo una rutina con hora tiene hora de aviso, y la medianoche exacta es sin hora`() {
        assertEquals(12, RoutineReminderPolicy.timeOf(routine(1, 30), zone)?.hour)
        assertNull(RoutineReminderPolicy.timeOf(routine(2, minutes = null), zone))
        assertNull(RoutineReminderPolicy.timeOf(routine(3, minutes = -12 * 60), zone))
        assertNull(RoutineReminderPolicy.timeOf(task(4, type = TaskType.TASK, startsInMinutes = 30), zone))
    }

    @Test
    fun `el proximo aviso es hoy si aun no llego la hora y mañana si ya paso`() {
        val routines = listOf(routine(1, 30))

        assertEquals(at(12, 30), RoutineReminderPolicy.next(routines, TEST_NOW, zone))
        assertEquals(at(12, 30, day = 11), RoutineReminderPolicy.next(routines, at(12, 30), zone))
    }

    @Test
    fun `una rutina de ciertos dias salta al proximo dia que toca`() {
        // El 10 de octubre de 2026 es sábado: la siguiente vez que toca es el lunes 12.
        val mondays = listOf(routine(1, 30, rule = "FREQ=WEEKLY;BYDAY=MO"))

        assertEquals(at(12, 30, day = 12), RoutineReminderPolicy.next(mondays, TEST_NOW, zone))
    }

    @Test
    fun `entre varias rutinas gana la mas proxima y las que no tienen hora no cuentan`() {
        val routines = listOf(routine(1, 90), routine(2, 30), routine(3, minutes = null))

        assertEquals(at(12, 30), RoutineReminderPolicy.next(routines, TEST_NOW, zone))
        assertNull(RoutineReminderPolicy.next(listOf(routine(3, minutes = null)), TEST_NOW, zone))
        assertNull(RoutineReminderPolicy.next(emptyList(), TEST_NOW, zone))
    }

    @Test
    fun `toca avisar desde su hora y hasta media hora despues, ni antes ni mucho despues`() {
        val routines = listOf(routine(1, 30))

        assertTrue(RoutineReminderPolicy.due(routines, at(12, 29), zone).isEmpty())
        assertEquals(1, RoutineReminderPolicy.due(routines, at(12, 30), zone).size)
        assertEquals(1, RoutineReminderPolicy.due(routines, at(12, 59), zone).size)
        assertTrue(RoutineReminderPolicy.due(routines, at(13, 0), zone).isEmpty())
    }

    @Test
    fun `una rutina que hoy no toca no avisa`() {
        val mondays = listOf(routine(1, 30, rule = "FREQ=WEEKLY;BYDAY=MO"))

        assertTrue(RoutineReminderPolicy.due(mondays, at(12, 30), zone).isEmpty())
        assertEquals(1, RoutineReminderPolicy.due(mondays, at(12, 30, day = 12), zone).size)
    }

    // ---- Avisar

    private val tasks = FakeTasks()
    private val completions = FakeRoutineCompletions()
    private val alarms = FakeRoutineAlarms()
    private val notifier = FakeRoutineNotifier()
    private val log = FakeRoutineLog()
    private val clock = MutableClock(at(12, 30))
    private val rewards = FakeRewards()
    private val ledger = testLedger(completions, rewards, clock = clock)
    private val schedule = ScheduleRoutineRemindersUseCase(tasks, alarms, clock)
    private val fire = FireRoutineRemindersUseCase(tasks, completions, notifier, log, schedule, clock)
    private val actions = RoutineReminderActionsUseCase(
        tasks, completions, SetTaskDoneUseCase(tasks, completions, clock, ledger), notifier, alarms, clock,
    )

    @Test
    fun `programar pone la alarma del proximo aviso, o la quita si no hay rutinas con hora`() = runTest {
        clock.now = TEST_NOW
        tasks.save(routine(1, 30))

        schedule()
        assertEquals(at(12, 30), alarms.general)

        tasks.delete(routine(1, 30))
        schedule()
        assertNull(alarms.general)
    }

    @Test
    fun `al sonar avisa de la rutina y deja la alarma para el siguiente dia`() = runTest {
        tasks.save(routine(1, 30))

        fire()

        assertEquals(listOf(FakeRoutineNotifier.Shown(routine(1, 30), canSnooze = true)), notifier.shown)
        assertEquals(at(12, 30, day = 11), alarms.general)
    }

    @Test
    fun `no avisa dos veces el mismo dia, aunque la alarma suene otra vez`() = runTest {
        tasks.save(routine(1, 30))

        fire()
        clock.now = at(12, 40)
        fire()

        assertEquals(1, notifier.shown.size)
    }

    @Test
    fun `una rutina ya hecha hoy no avisa`() = runTest {
        tasks.save(routine(1, 30))
        completions.markCompleted(1, TEST_TODAY, TEST_NOW)

        fire()

        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun `si el aviso llega muy tarde se deja pasar sin avisar`() = runTest {
        tasks.save(routine(1, 30))
        clock.now = at(15, 0)

        fire()

        assertTrue(notifier.shown.isEmpty())
        assertEquals(at(12, 30, day = 11), alarms.general)
    }

    @Test
    fun `varias rutinas a la misma hora avisan cada una una vez`() = runTest {
        tasks.save(routine(1, 30))
        tasks.save(routine(2, 30))

        fire()

        assertEquals(listOf(1L, 2L), notifier.shown.map { it.task.id })
    }

    // ---- Los botones

    @Test
    fun `hecha marca la rutina de hoy con su XP, quita el aviso y cancela el mas tarde`() = runTest {
        tasks.save(routine(1, 30))
        alarms.scheduleSnooze(1, at(13, 30))

        actions.done(1)

        assertEquals(setOf(1L to TEST_TODAY), completions.items)
        assertEquals(listOf(RewardCause.ROUTINE_DONE), rewards.events.map { it.cause })
        assertEquals(listOf(1L), notifier.dismissed)
        assertTrue(alarms.snoozes.isEmpty())
    }

    @Test
    fun `hecha sobre algo que no es una rutina no marca nada`() = runTest {
        tasks.save(task(1, type = TaskType.TASK))

        actions.done(1)

        assertTrue(completions.items.isEmpty())
        assertTrue(rewards.events.isEmpty())
    }

    @Test
    fun `mas tarde quita el aviso y lo recuerda una vez dentro de una hora`() = runTest {
        tasks.save(routine(1, 30))
        fire()

        actions.later(1)

        assertEquals(listOf(1L), notifier.dismissed)
        assertEquals(at(13, 30), alarms.snoozes[1L])
        assertTrue(completions.items.isEmpty())
    }

    @Test
    fun `al llegar el mas tarde avisa otra vez sin ofrecer aplazar mas`() = runTest {
        tasks.save(routine(1, 30))
        clock.now = at(13, 30)

        actions.snoozeElapsed(1)

        assertEquals(listOf(FakeRoutineNotifier.Shown(routine(1, 30), canSnooze = false)), notifier.shown)
    }

    @Test
    fun `si entretanto se hizo la rutina el mas tarde no avisa`() = runTest {
        tasks.save(routine(1, 30))
        completions.markCompleted(1, TEST_TODAY, TEST_NOW)
        clock.now = at(13, 30)

        actions.snoozeElapsed(1)

        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun `el mas tarde de una rutina borrada no hace nada`() = runTest {
        actions.snoozeElapsed(99)

        assertTrue(notifier.shown.isEmpty())
        assertFalse(alarms.snoozes.containsKey(99L))
    }
}
