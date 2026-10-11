package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.model.ObligationReminder
import com.focusdivergent.app.domain.model.Task
import com.focusdivergent.app.domain.model.TaskStatus
import com.focusdivergent.app.domain.model.TaskType
import com.focusdivergent.app.domain.reminder.ReminderSnoozes
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FindObligationReminderUseCaseTest {

    private fun find(vararg tasks: Task, snoozes: ReminderSnoozes = ReminderSnoozes()) =
        FindObligationReminderUseCase(FakeTasks(tasks.toList()), snoozes, TEST_CLOCK)

    @Test
    fun `una obligacion que empieza dentro de diez minutos o menos lanza el aviso`() = runTest {
        val reminder = find(obligation(1, startsInMinutes = 7, title = "Cita médica"))()

        assertEquals(ObligationReminder(1, "Cita médica", TEST_NOW.plusSeconds(7 * 60)), reminder)
    }

    @Test
    fun `justo a diez minutos ya salta, un segundo antes todavia no`() = runTest {
        assertEquals(1L, find(obligation(1, startsInMinutes = 10))()?.taskId)

        val tooEarly = obligation(1, startsInMinutes = 10).copy(startTime = TEST_NOW.plusSeconds(10 * 60 + 1))
        assertNull(find(tooEarly)())
    }

    @Test
    fun `una obligacion lejana no lanza el aviso`() = runTest {
        assertNull(find(obligation(1, startsInMinutes = 45))())
    }

    @Test
    fun `cuando llega la hora el aviso desaparece`() = runTest {
        assertNull(find(obligation(1, startsInMinutes = 0))())
        assertNull(find(obligation(2, startsInMinutes = -3))())
    }

    @Test
    fun `solo avisan las obligaciones pendientes`() = runTest {
        assertNull(find(obligation(1, 5, status = TaskStatus.DONE))())
        assertNull(find(obligation(2, 5, status = TaskStatus.IN_PROGRESS))())
    }

    @Test
    fun `una tarea o una rutina no lanzan aviso preventivo`() = runTest {
        assertNull(find(obligation(1, 5, type = TaskType.TASK), obligation(2, 5, type = TaskType.ROUTINE))())
    }

    @Test
    fun `con varias a la vez avisa de la mas proxima`() = runTest {
        val reminder = find(obligation(1, 9), obligation(2, 4), obligation(3, 6))()

        assertEquals(2L, reminder?.taskId)
    }

    @Test
    fun `un aviso pospuesto no sale hasta pasados los cinco minutos`() = runTest {
        val snoozes = ReminderSnoozes()
        val task = obligation(1, startsInMinutes = 8)
        snoozes.snooze(1, TEST_NOW.plus(Duration.ofMinutes(5)))

        assertNull(find(task, snoozes = snoozes)())

        // Cinco minutos después la posposición ha caducado y, aún antes de la hora, vuelve a aparecer.
        val later = FindObligationReminderUseCase(
            FakeTasks(listOf(task)),
            snoozes,
            Clock.fixed(TEST_NOW.plus(Duration.ofMinutes(5)), TEST_CLOCK.zone),
        )
        assertEquals(1L, later()?.taskId)
    }

    @Test
    fun `posponer una obligacion deja ver la siguiente que tambien toque`() = runTest {
        val snoozes = ReminderSnoozes().apply { snooze(1, TEST_NOW.plus(Duration.ofMinutes(5))) }

        assertEquals(2L, find(obligation(1, 4), obligation(2, 8), snoozes = snoozes)()?.taskId)
    }

    @Test
    fun `SnoozeObligationReminderUseCase pospone exactamente cinco minutos`() {
        val snoozes = ReminderSnoozes()

        SnoozeObligationReminderUseCase(snoozes, TEST_CLOCK)(taskId = 9)

        assertEquals(true, snoozes.isSnoozed(9, TEST_NOW))
        assertEquals(true, snoozes.isSnoozed(9, TEST_NOW.plusSeconds(5 * 60 - 1)))
        assertEquals(false, snoozes.isSnoozed(9, TEST_NOW.plusSeconds(5 * 60)))
        assertEquals(false, snoozes.isSnoozed(10, TEST_NOW))
    }
}
