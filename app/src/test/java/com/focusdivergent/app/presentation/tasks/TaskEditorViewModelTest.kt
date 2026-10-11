package com.focusdivergent.app.presentation.tasks

import androidx.lifecycle.SavedStateHandle
import com.focusdivergent.app.domain.model.EnergyLevel
import com.focusdivergent.app.domain.model.TaskType
import com.focusdivergent.app.domain.tasks.Recurrence
import com.focusdivergent.app.domain.tasks.TaskProblem
import com.focusdivergent.app.domain.tasks.TaskTrash
import com.focusdivergent.app.domain.model.BrainDump
import com.focusdivergent.app.domain.usecase.DeleteTaskUseCase
import com.focusdivergent.app.domain.usecase.FakeBrainDump
import com.focusdivergent.app.domain.usecase.FakeTaskSteps
import com.focusdivergent.app.domain.usecase.FakeTasks
import com.focusdivergent.app.domain.usecase.testLedger
import com.focusdivergent.app.domain.usecase.SaveTaskUseCase
import com.focusdivergent.app.domain.usecase.TEST_CLOCK
import com.focusdivergent.app.domain.usecase.TEST_NOW
import com.focusdivergent.app.domain.usecase.TEST_TODAY
import com.focusdivergent.app.domain.usecase.task
import com.focusdivergent.app.presentation.MainDispatcherRule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TaskEditorViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val tasks = FakeTasks()
    private val trash = TaskTrash()
    private val brainDump = FakeBrainDump()
    private val steps = FakeTaskSteps()

    private fun viewModel(taskId: Long = 0, title: String = "", ideaId: Long = 0) = TaskEditorViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(
                TaskEditorViewModel.ARG_TASK_ID to taskId,
                TaskEditorViewModel.ARG_TITLE to title,
                TaskEditorViewModel.ARG_IDEA_ID to ideaId,
            ),
        ),
        tasks = tasks,
        taskSteps = steps,
        brainDump = brainDump,
        saveTask = SaveTaskUseCase(tasks, TEST_CLOCK),
        deleteTask = DeleteTaskUseCase(tasks, trash, testLedger()),
        clock = TEST_CLOCK,
    )

    private suspend fun TaskEditorViewModel.closed(): Boolean =
        withTimeoutOrNull(100) { events.first() } == TaskEditorEvent.Close

    @Test
    fun `una actividad nueva arranca vacia, como tarea de energia media y sin detalles a la vista`() {
        val state = viewModel().uiState.value

        assertTrue(state.draft.isNew)
        assertFalse(state.loading)
        assertEquals("", state.draft.title)
        assertEquals(TaskType.TASK, state.draft.type)
        assertEquals(EnergyLevel.MEDIUM, state.draft.energy)
        assertFalse(state.detailsExpanded)
        assertEquals(TEST_TODAY, state.today)
    }

    @Test
    fun `una actividad que nace de una idea arranca con su titulo`() {
        val state = viewModel(title = "Llamar a Marta", ideaId = 4).uiState.value

        assertEquals("Llamar a Marta", state.draft.title)
        assertTrue(state.draft.isNew)
        assertTrue(state.problems.isEmpty())
    }

    @Test
    fun `guardar la actividad de una idea la da por procesada`() = runTest {
        brainDump.add(BrainDump(id = 4, content = "Llamar a Marta", timestamp = TEST_NOW))
        val vm = viewModel(title = "Llamar a Marta", ideaId = 4)

        vm.save()

        assertTrue(vm.closed())
        assertEquals("Llamar a Marta", tasks.items.single().title)
        assertTrue(brainDump.observeUnprocessed().first().isEmpty())
    }

    @Test
    fun `salir sin guardar deja la idea esperando en la bandeja`() = runTest {
        brainDump.add(BrainDump(id = 4, content = "Llamar a Marta", timestamp = TEST_NOW))
        val vm = viewModel(title = "Llamar a Marta", ideaId = 4)

        // Sin título no se guarda: la idea sigue sin procesar.
        vm.setTitle("")
        vm.save()

        assertFalse(vm.closed())
        assertEquals(listOf(4L), brainDump.observeUnprocessed().first().map { it.id })
        assertTrue(tasks.items.isEmpty())
    }

    @Test
    fun `editar una actividad existente ignora el titulo de la ruta`() = runTest {
        tasks.save(task(9, title = "Original"))

        val vm = viewModel(taskId = 9, title = "Otro")

        assertEquals("Original", vm.uiState.first { !it.loading }.draft.title)
    }

    @Test
    fun `guardar con solo el titulo crea la tarea y cierra el editor`() = runTest {
        val vm = viewModel()

        vm.setTitle("Tender la ropa")
        vm.setEnergy(EnergyLevel.LOW)
        vm.save()

        assertTrue(vm.closed())
        assertEquals("Tender la ropa", tasks.items.single().title)
        assertEquals(EnergyLevel.LOW, tasks.items.single().energyLevel)
    }

    @Test
    fun `sin titulo no se guarda, se avisa y el aviso desaparece al escribir`() = runTest {
        val vm = viewModel()

        vm.save()

        assertFalse(vm.closed())
        assertEquals(setOf(TaskProblem.TITLE_BLANK), vm.uiState.value.problems)
        assertTrue(tasks.items.isEmpty())

        vm.setTitle("A")
        assertTrue(vm.uiState.value.problems.isEmpty())
    }

    @Test
    fun `una obligacion sin hora avisa de lo que falta y se guarda al completarla`() = runTest {
        val vm = viewModel()
        vm.setTitle("Cita médica")
        vm.setType(TaskType.OBLIGATION)

        vm.save()
        assertEquals(setOf(TaskProblem.OBLIGATION_NEEDS_DATE_AND_TIME), vm.uiState.value.problems)

        vm.setDate(TEST_TODAY)
        assertTrue(vm.uiState.value.problems.isEmpty())
        vm.setTime(LocalTime.of(17, 30))
        vm.save()

        assertTrue(vm.closed())
        assertEquals(Instant.parse("2026-10-10T17:30:00Z"), tasks.items.single().startTime)
    }

    @Test
    fun `los avisos solo aparecen al intentar guardar, no mientras se rellena`() {
        val vm = viewModel()

        vm.setType(TaskType.OBLIGATION)
        vm.setTitle("")

        assertTrue(vm.uiState.value.problems.isEmpty())
    }

    @Test
    fun `al editar se cargan los datos y los detalles que ya tenia quedan a la vista`() = runTest {
        tasks.save(task(4, title = "Dentista", type = TaskType.OBLIGATION, startsInMinutes = 90, estimatedMinutes = 30))

        val state = viewModel(taskId = 4).uiState.value

        assertFalse(state.loading)
        assertFalse(state.draft.isNew)
        assertEquals("Dentista", state.draft.title)
        assertEquals(TEST_TODAY, state.draft.date)
        assertEquals(LocalTime.of(13, 30), state.draft.time)
        assertEquals(30, state.draft.estimatedMinutes)
        assertTrue(state.detailsExpanded)
    }

    @Test
    fun `editar una rutina semanal recupera sus dias, y tocarlos cambia la regla`() = runTest {
        tasks.save(task(2, type = TaskType.ROUTINE, recurrenceRule = "FREQ=WEEKLY;BYDAY=MO,FR"))
        val vm = viewModel(taskId = 2)

        assertTrue(vm.uiState.value.weekly)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), vm.uiState.value.weekDays)

        vm.toggleWeekDay(DayOfWeek.FRIDAY)
        vm.toggleWeekDay(DayOfWeek.SUNDAY)
        vm.save()

        assertEquals("FREQ=WEEKLY;BYDAY=MO,SU", tasks.items.single().recurrenceRule)
    }

    @Test
    fun `algunos dias sin ninguno marcado se guarda como todos los dias`() = runTest {
        val vm = viewModel()
        vm.setTitle("Medicación")
        vm.setType(TaskType.ROUTINE)

        vm.setWeekly(true)
        assertEquals(Recurrence.Daily, vm.uiState.value.draft.recurrence)
        vm.toggleWeekDay(DayOfWeek.TUESDAY)
        assertEquals(Recurrence.Weekly(setOf(DayOfWeek.TUESDAY)), vm.uiState.value.draft.recurrence)

        vm.setWeekly(false)
        vm.save()
        assertEquals("FREQ=DAILY", tasks.items.single().recurrenceRule)
    }

    @Test
    fun `borrar quita la actividad, la deja en la papelera y cierra`() = runTest {
        val original = task(9)
        tasks.save(original)
        val vm = viewModel(taskId = 9)

        vm.delete()

        assertTrue(vm.closed())
        assertTrue(tasks.items.isEmpty())
        assertEquals(original, trash.lastDeleted.value)
    }

    @Test
    fun `abrir una actividad que ya no existe cierra el editor`() = runTest {
        assertTrue(viewModel(taskId = 404).closed())
    }

    // ---- Pasos

    private suspend fun TaskEditorViewModel.nextEvent(): TaskEditorEvent? = withTimeoutOrNull(100) { events.first() }

    @Test
    fun `dividir en pasos una actividad nueva la guarda primero y abre sus pasos sin duplicarla al guardar`() = runTest {
        val vm = viewModel()
        vm.setTitle("Informe")

        vm.openSteps()

        val saved = tasks.items.single()
        assertEquals(TaskEditorEvent.OpenSteps(saved.id), vm.nextEvent())
        assertEquals(saved.id, vm.uiState.value.draft.original?.id)

        // Al volver del editor de pasos, guardar actualiza la misma actividad en vez de crear otra.
        vm.setTitle("Informe final")
        vm.save()
        assertEquals(listOf("Informe final"), tasks.items.map { it.title })
    }

    @Test
    fun `dividir en pasos sin titulo no guarda ni abre nada, y avisa de lo que falta`() = runTest {
        val vm = viewModel()

        vm.openSteps()

        assertTrue(tasks.items.isEmpty())
        assertEquals(setOf(TaskProblem.TITLE_BLANK), vm.uiState.value.problems)
        assertEquals(null, vm.nextEvent())
    }

    @Test
    fun `dividir en pasos desde una idea del Brain Dump la da por procesada`() = runTest {
        brainDump.add(BrainDump(id = 5, content = "Llamar al banco", timestamp = TEST_NOW))
        val vm = viewModel(title = "Llamar al banco", ideaId = 5)

        vm.openSteps()

        assertTrue(brainDump.items.single { it.id == 5L }.isProcessed)
    }

    @Test
    fun `una actividad ya guardada abre sus pasos sin guardar otra vez`() = runTest {
        tasks.save(task(9, title = "Informe"))
        val vm = viewModel(taskId = 9)

        vm.openSteps()

        assertEquals(TaskEditorEvent.OpenSteps(9), vm.nextEvent())
        assertEquals(1, tasks.items.size)
    }

    @Test
    fun `el editor lleva la cuenta de los pasos hechos de la actividad`() = runTest {
        tasks.save(task(9, title = "Informe"))
        steps.addAll(9, listOf("Uno", "Dos", "Tres"))
        steps.setDone(steps.items.first().id, true)

        val vm = viewModel(taskId = 9)

        assertEquals(1, vm.uiState.value.steps.done)
        assertEquals(3, vm.uiState.value.steps.total)
        assertFalse(viewModel().uiState.value.steps.hasSteps)
    }

    @Test
    fun `guardar algo incompleto abre los detalles para que se vea que falta`() = runTest {
        val vm = viewModel()
        vm.setType(TaskType.OBLIGATION)
        assertFalse(vm.uiState.value.detailsExpanded)

        vm.save()

        assertTrue(vm.uiState.value.detailsExpanded)
        assertTrue(tasks.items.isEmpty())
    }
}
