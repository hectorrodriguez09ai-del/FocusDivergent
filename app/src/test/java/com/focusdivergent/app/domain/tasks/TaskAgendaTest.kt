package com.focusdivergent.app.domain.tasks

import com.focusdivergent.app.domain.model.EnergyLevel
import com.focusdivergent.app.domain.model.Task
import com.focusdivergent.app.domain.model.TaskStatus
import com.focusdivergent.app.domain.model.TaskType
import com.focusdivergent.app.domain.usecase.TEST_NOW
import com.focusdivergent.app.domain.usecase.TEST_TODAY
import com.focusdivergent.app.domain.usecase.task
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** «Ahora» es el sábado 10 de octubre de 2026 a las 12:00 UTC. */
class TaskAgendaTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private val day = 24 * 60L

    private fun overview(
        vararg tasks: Task,
        routinesDone: Set<Long> = emptySet(),
        filter: TaskFilter = TaskFilter(),
        zone: ZoneId = utc,
        today: LocalDate = TEST_TODAY,
    ) = TaskAgenda.overview(tasks.toList(), routinesDone, today, zone, filter)

    private fun Map<TaskSection, List<AgendaItem>>.ids(section: TaskSection) = this[section].orEmpty().map { it.task.id }

    @Test
    fun `cada actividad pendiente va a su bloque segun su fecha`() {
        val result = overview(
            task(1, startsInMinutes = 60),          // hoy a las 13:00
            task(2, startsInMinutes = 2 * day),     // el lunes
            task(3),                                // sin fecha
        )

        assertEquals(listOf(1L), result.ids(TaskSection.TODAY))
        assertEquals(listOf(2L), result.ids(TaskSection.UPCOMING))
        assertEquals(listOf(3L), result.ids(TaskSection.SOMEDAY))
    }

    @Test
    fun `lo de dias pasados que sigue pendiente aparece en Hoy sin distinguirse`() {
        val result = overview(task(1, startsInMinutes = -3 * day), task(2, startsInMinutes = 30))

        // No hay bloque de «atrasadas»: ambas son cosas que se pueden hacer hoy.
        assertEquals(setOf(TaskSection.TODAY), result.keys)
        assertEquals(setOf(1L, 2L), result.ids(TaskSection.TODAY).toSet())
        assertFalse(result.getValue(TaskSection.TODAY).any { it.isDone })
    }

    @Test
    fun `lo hecho hoy va a Hechas hoy y lo hecho otros dias no se muestra`() {
        val result = overview(
            task(1, status = TaskStatus.DONE, completedAt = TEST_NOW.minusSeconds(3600)),
            task(2, status = TaskStatus.DONE, completedAt = TEST_NOW.minusSeconds(30 * 3600)),
            task(3, status = TaskStatus.DONE), // hecha antes de que se guardara la fecha
        )

        assertEquals(listOf(1L), result.ids(TaskSection.DONE_TODAY))
        assertTrue(result.getValue(TaskSection.DONE_TODAY).single().isDone)
        assertEquals(setOf(TaskSection.DONE_TODAY), result.keys)
    }

    @Test
    fun `una rutina que toca hoy esta en Hoy hasta que se marca`() {
        val routine = task(1, type = TaskType.ROUTINE, recurrenceRule = "FREQ=DAILY")

        assertEquals(listOf(1L), overview(routine).ids(TaskSection.TODAY))

        val done = overview(routine, routinesDone = setOf(1L))
        assertEquals(listOf(1L), done.ids(TaskSection.DONE_TODAY))
        assertTrue(done.ids(TaskSection.TODAY).isEmpty())
    }

    @Test
    fun `una rutina que hoy no toca va a Proximamente con su proximo dia`() {
        val mondays = task(1, type = TaskType.ROUTINE, recurrenceRule = "FREQ=WEEKLY;BYDAY=MO")

        val item = overview(mondays).getValue(TaskSection.UPCOMING).single()

        assertEquals(LocalDate.of(2026, 10, 12), item.date)
    }

    @Test
    fun `el estado guardado de una rutina no la saca de la lista`() {
        // Una rutina se marca por día; aunque su estado dijera DONE, mañana vuelve a tocar.
        val routine = task(1, type = TaskType.ROUTINE, status = TaskStatus.DONE)

        assertEquals(listOf(1L), overview(routine).ids(TaskSection.TODAY))
    }

    @Test
    fun `Hoy se ordena por hora y deja al final lo que no tiene`() {
        val result = overview(
            task(1, type = TaskType.ROUTINE),            // rutina sin hora
            task(2, startsInMinutes = 180),              // 15:00
            task(3, startsInMinutes = -120),             // 10:00
            task(4, startsInMinutes = -day - 60),        // ayer a las 11:00
        )

        assertEquals(listOf(3L, 4L, 2L, 1L), result.ids(TaskSection.TODAY))
    }

    @Test
    fun `Proximamente se ordena por dia y despues por hora`() {
        val result = overview(
            task(1, startsInMinutes = 3 * day),
            task(2, startsInMinutes = day + 120),
            task(3, startsInMinutes = day - 60),
        )

        assertEquals(listOf(3L, 2L, 1L), result.ids(TaskSection.UPCOMING))
    }

    @Test
    fun `una tarea con dia pero sin hora no ensena la medianoche`() {
        // Guardada a las 00:00 de hoy: es «hoy, sin hora».
        val allDay = task(1, startsInMinutes = -12 * 60)
        val obligationAtMidnight = task(2, type = TaskType.OBLIGATION, startsInMinutes = 12 * 60)

        val result = overview(allDay, obligationAtMidnight)

        assertNull(result.getValue(TaskSection.TODAY).single().time)
        // Una Obligación siempre tiene hora real, aunque sea medianoche.
        assertEquals(LocalTime.MIDNIGHT, result.getValue(TaskSection.UPCOMING).single().time)
    }

    @Test
    fun `el dia se calcula en la zona horaria del usuario`() {
        // 23:30 UTC del sábado es ya domingo en Madrid (UTC+2).
        val late = task(1, startsInMinutes = 11 * 60 + 30)

        assertEquals(listOf(1L), overview(late).ids(TaskSection.TODAY))
        assertEquals(listOf(1L), overview(late, zone = ZoneId.of("Europe/Madrid")).ids(TaskSection.UPCOMING))
    }

    @Test
    fun `el filtro de energia y el de tipo se combinan`() {
        val tasks = arrayOf(
            task(1, energy = EnergyLevel.LOW),
            task(2, energy = EnergyLevel.HIGH),
            task(3, energy = EnergyLevel.LOW, type = TaskType.ROUTINE),
        )

        assertEquals(setOf(1L, 3L), overview(*tasks, filter = TaskFilter(energy = EnergyLevel.LOW)).values.flatten().map { it.task.id }.toSet())
        assertEquals(listOf(3L), overview(*tasks, filter = TaskFilter(EnergyLevel.LOW, TaskType.ROUTINE)).values.flatten().map { it.task.id })
        assertTrue(overview(*tasks, filter = TaskFilter(EnergyLevel.MEDIUM)).isEmpty())
        assertFalse(TaskFilter().isActive)
        assertTrue(TaskFilter(type = TaskType.TASK).isActive)
    }

    @Test
    fun `la agenda de un dia trae lo fechado ese dia y las rutinas que tocan`() {
        val monday = LocalDate.of(2026, 10, 12)
        val tasks = listOf(
            task(1, startsInMinutes = 2 * day + 180),                                       // lunes 15:00
            task(2, startsInMinutes = 2 * day - 120, status = TaskStatus.DONE),             // lunes 10:00, hecha
            task(3, startsInMinutes = 60),                                                  // sábado
            task(4, type = TaskType.ROUTINE, recurrenceRule = "FREQ=WEEKLY;BYDAY=MO"),
            task(5, type = TaskType.ROUTINE, recurrenceRule = "FREQ=WEEKLY;BYDAY=TU"),
            task(6),                                                                        // sin fecha
        )

        val agenda = TaskAgenda.forDay(tasks, routinesDoneThatDay = setOf(4L), day = monday, zone = utc)

        assertEquals(listOf(2L, 1L, 4L), agenda.map { it.task.id })
        assertEquals(listOf(true, false, true), agenda.map { it.isDone })
    }

    @Test
    fun `la semana empieza en el primer dia indicado y contiene el dia pedido`() {
        val week = TaskAgenda.weekOf(TEST_TODAY, DayOfWeek.MONDAY)

        assertEquals(LocalDate.of(2026, 10, 5), week.first())
        assertEquals(LocalDate.of(2026, 10, 11), week.last())
        assertEquals(7, week.size)
        assertTrue(TEST_TODAY in week)

        // En países donde la semana empieza en domingo.
        assertEquals(LocalDate.of(2026, 10, 4), TaskAgenda.weekOf(TEST_TODAY, DayOfWeek.SUNDAY).first())
        // El primer día de la semana se devuelve a sí mismo como inicio.
        assertEquals(LocalDate.of(2026, 10, 5), TaskAgenda.weekOf(LocalDate.of(2026, 10, 5), DayOfWeek.MONDAY).first())
    }
}
