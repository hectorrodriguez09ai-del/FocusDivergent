package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.model.Task
import com.focusdivergent.app.domain.model.TaskStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleNextObligationAlarmUseCaseTest {

    private val scheduler = FakeAlarmScheduler()

    private fun schedule(vararg tasks: Task) =
        ScheduleNextObligationAlarmUseCase(FakeTasks(tasks.toList()), scheduler, TEST_CLOCK)

    @Test
    fun `programa la alarma diez minutos antes de la proxima obligacion`() = runTest {
        schedule(obligation(1, startsInMinutes = 60))()

        assertEquals(TEST_NOW.plusSeconds(50 * 60), scheduler.scheduledAt)
    }

    @Test
    fun `elige la mas cercana de las que aun no han llegado a su aviso`() = runTest {
        schedule(obligation(1, 180), obligation(2, 40), obligation(3, 90))()

        assertEquals(TEST_NOW.plusSeconds(30 * 60), scheduler.scheduledAt)
    }

    @Test
    fun `una obligacion que ya esta dentro de su margen la cubre el servicio y se programa la siguiente`() = runTest {
        schedule(obligation(1, startsInMinutes = 6), obligation(2, startsInMinutes = 120))()

        assertEquals(TEST_NOW.plusSeconds(110 * 60), scheduler.scheduledAt)
    }

    @Test
    fun `justo a diez minutos el aviso ya es ahora y no se programa`() = runTest {
        schedule(obligation(1, startsInMinutes = 10))()

        assertNull(scheduler.scheduledAt)
        assertEquals(1, scheduler.cancels)
    }

    @Test
    fun `sin obligaciones pendientes cancela la alarma`() = runTest {
        schedule(obligation(1, 60, status = TaskStatus.DONE), obligation(2, -30))()

        assertNull(scheduler.scheduledAt)
        assertEquals(1, scheduler.cancels)
    }
}
