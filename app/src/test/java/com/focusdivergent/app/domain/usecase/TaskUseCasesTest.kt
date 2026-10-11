package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.model.EnergyLevel
import com.focusdivergent.app.domain.model.TaskStatus
import com.focusdivergent.app.domain.model.TaskType
import com.focusdivergent.app.domain.tasks.Recurrence
import com.focusdivergent.app.domain.tasks.TaskDraft
import com.focusdivergent.app.domain.tasks.TaskProblem
import com.focusdivergent.app.domain.tasks.TaskTrash
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskUseCasesTest {

    private val tasks = FakeTasks()
    private val completions = FakeRoutineCompletions()
    private val trash = TaskTrash()
    private val save = SaveTaskUseCase(tasks, TEST_CLOCK)
    private val rewards = FakeRewards()
    private val stats = FakeUserStats()
    private val ledger = testLedger(completions, rewards, stats)
    private val setDone = SetTaskDoneUseCase(tasks, completions, TEST_CLOCK, ledger)

    // ---- Guardar

    @Test
    fun `con solo el titulo se guarda una tarea de energia media sin fecha`() = runTest {
        val result = save(TaskDraft(title = "  Regar las plantas  "))

        val saved = tasks.items.single()
        assertEquals(SaveTaskResult.Saved(saved.id), result)
        assertEquals("Regar las plantas", saved.title)
        assertEquals(TaskType.TASK, saved.type)
        assertEquals(EnergyLevel.MEDIUM, saved.energyLevel)
        assertEquals(TaskStatus.PENDING, saved.status)
        assertNull(saved.startTime)
        assertNull(saved.recurrenceRule)
    }

    @Test
    fun `sin titulo no se guarda y se dice que falta`() = runTest {
        assertEquals(SaveTaskResult.Invalid(setOf(TaskProblem.TITLE_BLANK)), save(TaskDraft(title = "   ")))
        assertTrue(tasks.items.isEmpty())
    }

    @Test
    fun `una obligacion necesita dia y hora`() = runTest {
        val base = TaskDraft(title = "Cita médica", type = TaskType.OBLIGATION)
        val needs = SaveTaskResult.Invalid(setOf(TaskProblem.OBLIGATION_NEEDS_DATE_AND_TIME))

        assertEquals(needs, save(base))
        assertEquals(needs, save(base.copy(date = TEST_TODAY)))
        assertEquals(needs, save(base.copy(time = LocalTime.of(10, 0))))
        assertTrue(tasks.items.isEmpty())

        save(base.copy(date = TEST_TODAY, time = LocalTime.of(17, 30)))
        assertEquals(Instant.parse("2026-10-10T17:30:00Z"), tasks.items.single().startTime)
    }

    @Test
    fun `se avisa de todo lo que falta a la vez`() = runTest {
        val result = save(TaskDraft(title = "", type = TaskType.OBLIGATION))

        assertEquals(
            SaveTaskResult.Invalid(setOf(TaskProblem.TITLE_BLANK, TaskProblem.OBLIGATION_NEEDS_DATE_AND_TIME)),
            result,
        )
    }

    @Test
    fun `una tarea con dia pero sin hora se guarda a medianoche de ese dia`() = runTest {
        save(TaskDraft(title = "Comprar regalo", date = TEST_TODAY.plusDays(2)))

        assertEquals(Instant.parse("2026-10-12T00:00:00Z"), tasks.items.single().startTime)
    }

    @Test
    fun `una tarea con hora pero sin dia se entiende que es hoy`() = runTest {
        save(TaskDraft(title = "Llamar", time = LocalTime.of(18, 0)))

        assertEquals(Instant.parse("2026-10-10T18:00:00Z"), tasks.items.single().startTime)
    }

    @Test
    fun `el dia y la hora se guardan en la zona horaria del usuario`() = runTest {
        val madrid = Clock.fixed(TEST_NOW, ZoneId.of("Europe/Madrid"))

        SaveTaskUseCase(tasks, madrid)(TaskDraft(title = "Cita", type = TaskType.OBLIGATION, date = TEST_TODAY, time = LocalTime.of(17, 30)))

        // Las 17:30 de Madrid en octubre (UTC+2) son las 15:30 UTC.
        assertEquals(Instant.parse("2026-10-10T15:30:00Z"), tasks.items.single().startTime)
    }

    @Test
    fun `una rutina guarda su regla y solo su hora, y el resto de tipos no guardan regla`() = runTest {
        val weekly = Recurrence.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))
        save(TaskDraft(title = "Medicación", type = TaskType.ROUTINE, recurrence = weekly, time = LocalTime.of(9, 0), date = TEST_TODAY.plusDays(9)))
        save(TaskDraft(title = "Tarea", type = TaskType.TASK, recurrence = weekly))

        val (routine, task) = tasks.items
        assertEquals("FREQ=WEEKLY;BYDAY=MO,TH", routine.recurrenceRule)
        assertEquals(LocalTime.of(9, 0), routine.startTime!!.atZone(TEST_CLOCK.zone).toLocalTime())
        assertNull(task.recurrenceRule)
    }

    @Test
    fun `la duracion solo se guarda si es positiva`() = runTest {
        save(TaskDraft(title = "A", estimatedMinutes = 15))
        save(TaskDraft(title = "B", estimatedMinutes = 0))

        assertEquals(listOf(15, null), tasks.items.map { it.estimatedMinutes })
    }

    @Test
    fun `editar conserva el id, el estado y cuando se completo`() = runTest {
        val original = task(7, title = "Antes", status = TaskStatus.DONE, completedAt = TEST_NOW, startsInMinutes = 60)
        tasks.save(original)

        val result = save(TaskDraft.from(original, TEST_CLOCK.zone).copy(title = "Después", energy = EnergyLevel.HIGH))

        val edited = tasks.items.single()
        assertEquals(SaveTaskResult.Saved(7), result)
        assertEquals("Después", edited.title)
        assertEquals(EnergyLevel.HIGH, edited.energyLevel)
        assertEquals(TaskStatus.DONE, edited.status)
        assertEquals(TEST_NOW, edited.completedAt)
        assertEquals(original.startTime, edited.startTime)
    }

    @Test
    fun `abrir y guardar sin tocar nada no cambia la actividad`() = runTest {
        val originals = listOf(
            task(1, type = TaskType.OBLIGATION, startsInMinutes = 90, estimatedMinutes = 30),
            task(2, startsInMinutes = -12 * 60),            // hoy, sin hora
            task(3),                                        // sin fecha
            task(4, type = TaskType.ROUTINE, recurrenceRule = "FREQ=WEEKLY;BYDAY=MO,FR"),
        )

        for (original in originals) {
            tasks.save(original)
            save(TaskDraft.from(original, TEST_CLOCK.zone))
            assertEquals(original, tasks.getById(original.id))
        }
    }

    // ---- El borrador

    @Test
    fun `el borrador separa el dia de la hora y esconde la medianoche de una tarea`() {
        val timed = TaskDraft.from(task(1, startsInMinutes = 90), TEST_CLOCK.zone)
        assertEquals(TEST_TODAY, timed.date)
        assertEquals(LocalTime.of(13, 30), timed.time)
        assertFalse(timed.isNew)

        val allDay = TaskDraft.from(task(2, startsInMinutes = -12 * 60), TEST_CLOCK.zone)
        assertEquals(TEST_TODAY, allDay.date)
        assertNull(allDay.time)

        val routine = TaskDraft.from(task(3, type = TaskType.ROUTINE, startsInMinutes = 60, recurrenceRule = "FREQ=WEEKLY;BYDAY=SA"), TEST_CLOCK.zone)
        assertNull(routine.date)
        assertEquals(LocalTime.of(13, 0), routine.time)
        assertEquals(Recurrence.Weekly(setOf(DayOfWeek.SATURDAY)), routine.recurrence)

        assertTrue(TaskDraft().isNew)
    }

    // ---- Marcar como hecha

    @Test
    fun `marcar una tarea la pasa a hecha con la hora de ahora, y desmarcarla la devuelve a pendiente`() = runTest {
        tasks.save(task(1))

        assertTrue(setDone(tasks.items.single(), done = true))
        assertEquals(TaskStatus.DONE, tasks.items.single().status)
        assertEquals(TEST_NOW, tasks.items.single().completedAt)

        assertTrue(setDone(tasks.items.single(), done = false))
        assertEquals(TaskStatus.PENDING, tasks.items.single().status)
        assertNull(tasks.items.single().completedAt)
    }

    @Test
    fun `marcar una rutina solo la marca ese dia y no toca su estado`() = runTest {
        val routine = task(1, type = TaskType.ROUTINE)
        tasks.save(routine)

        setDone(routine, done = true)
        assertEquals(setOf(1L to TEST_TODAY), completions.items)
        assertEquals(TaskStatus.PENDING, tasks.items.single().status)

        // Un día pasado se puede marcar sin afectar a hoy.
        setDone(routine, done = true, day = TEST_TODAY.minusDays(1))
        setDone(routine, done = false)
        assertEquals(setOf(1L to TEST_TODAY.minusDays(1)), completions.items)
    }

    @Test
    fun `no se puede marcar una rutina de un dia que aun no ha llegado`() = runTest {
        val routine = task(1, type = TaskType.ROUTINE)

        assertFalse(setDone(routine, done = true, day = TEST_TODAY.plusDays(1)))
        assertTrue(completions.items.isEmpty())
    }

    // ---- Borrar y deshacer

    @Test
    fun `borrar deja la actividad en la papelera y deshacer la devuelve con su mismo id`() = runTest {
        val original = task(5, title = "Renovar DNI", startsInMinutes = 60, estimatedMinutes = 30)
        tasks.save(original)
        tasks.save(task(6))

        DeleteTaskUseCase(tasks, trash, ledger)(original)
        assertEquals(listOf(6L), tasks.items.map { it.id })
        assertEquals(original, trash.lastDeleted.value)

        RestoreDeletedTaskUseCase(tasks, trash)()
        assertEquals(original, tasks.getById(5))
        assertNull(trash.lastDeleted.value)
    }

    @Test
    fun `deshacer sin nada en la papelera no hace nada`() = runTest {
        tasks.save(task(1))

        RestoreDeletedTaskUseCase(tasks, trash)()

        assertEquals(1, tasks.items.size)
    }

    @Test
    fun `cerrar el aviso de un borrado antiguo no vacia uno mas reciente`() {
        val first = task(1)
        val second = task(2)
        trash.put(first)
        trash.put(second)

        trash.clear(first)

        assertEquals(second, trash.lastDeleted.value)
        trash.clear(second)
        assertNull(trash.lastDeleted.value)
    }
}
