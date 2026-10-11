package com.focusdivergent.app.presentation.tasks

import com.focusdivergent.app.domain.model.EnergyLevel
import com.focusdivergent.app.domain.model.TaskStatus
import com.focusdivergent.app.domain.model.TaskType
import com.focusdivergent.app.domain.tasks.TaskSection
import com.focusdivergent.app.domain.tasks.TaskTrash
import com.focusdivergent.app.domain.usecase.DeleteTaskUseCase
import com.focusdivergent.app.domain.usecase.FakeRoutineCompletions
import com.focusdivergent.app.domain.usecase.FakeTasks
import com.focusdivergent.app.domain.usecase.testLedger
import com.focusdivergent.app.domain.usecase.RestoreDeletedTaskUseCase
import com.focusdivergent.app.domain.usecase.SetTaskDoneUseCase
import com.focusdivergent.app.domain.usecase.TEST_CLOCK
import com.focusdivergent.app.domain.usecase.TEST_TODAY
import com.focusdivergent.app.domain.usecase.task
import com.focusdivergent.app.presentation.MainDispatcherRule
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TasksViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val day = 24 * 60L
    private val tasks = FakeTasks()
    private val completions = FakeRoutineCompletions()
    private val trash = TaskTrash()

    /** Reloj que el test puede adelantar, para probar el cambio de día. */
    private class MutableClock(var now: Instant) : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
    }

    private fun viewModel(clock: Clock = TEST_CLOCK) = TasksViewModel(
        tasks = tasks,
        completions = completions,
        trash = trash,
        setTaskDone = SetTaskDoneUseCase(tasks, completions, clock, testLedger(completions, clock = clock)),
        restoreDeletedTask = RestoreDeletedTaskUseCase(tasks, trash),
        clock = clock,
    )

    // El estado solo se calcula mientras alguien lo observa, como hace la pantalla.
    private fun TestScope.observe(vm: TasksViewModel) {
        backgroundScope.launch(mainDispatcher.dispatcher) { vm.uiState.collect() }
    }

    private fun TasksViewModel.ids(section: TaskSection?) =
        uiState.value.sections.firstOrNull { it.section == section }?.items.orEmpty().map { it.task.id }

    private fun TasksViewModel.allIds() = uiState.value.sections.flatMap { it.items }.map { it.task.id }

    @Test
    fun `arranca en hoy, con la semana que lo contiene y la lista repartida en bloques`() = runTest {
        tasks.save(task(1, startsInMinutes = 60))
        tasks.save(task(2, startsInMinutes = 3 * day))
        tasks.save(task(3))
        val vm = viewModel().also { observe(it) }

        val state = vm.uiState.value
        assertEquals(TEST_TODAY, state.selectedDay)
        assertTrue(state.isToday)
        assertEquals(7, state.week.size)
        assertTrue(TEST_TODAY in state.week)
        assertFalse(state.loading)
        assertTrue(state.hasAnyTask)
        assertEquals(listOf(1L), vm.ids(TaskSection.TODAY))
        assertEquals(listOf(2L), vm.ids(TaskSection.UPCOMING))
        assertEquals(listOf(3L), vm.ids(TaskSection.SOMEDAY))
    }

    @Test
    fun `elegir otro dia muestra solo la agenda de ese dia`() = runTest {
        tasks.save(task(1, startsInMinutes = 60))
        tasks.save(task(2, startsInMinutes = 2 * day))
        tasks.save(task(3))
        val vm = viewModel().also { observe(it) }

        vm.selectDay(TEST_TODAY.plusDays(2))

        assertFalse(vm.uiState.value.isToday)
        assertEquals(listOf(2L), vm.ids(null))
        assertEquals(1, vm.uiState.value.sections.size)

        vm.goToToday()
        assertEquals(listOf(1L), vm.ids(TaskSection.TODAY))
    }

    @Test
    fun `las flechas mueven una semana y conservan el dia de la semana`() = runTest {
        val vm = viewModel().also { observe(it) }

        vm.nextWeek()
        assertEquals(TEST_TODAY.plusDays(7), vm.uiState.value.selectedDay)
        assertTrue(TEST_TODAY.plusDays(7) in vm.uiState.value.week)
        assertFalse(TEST_TODAY in vm.uiState.value.week)

        vm.previousWeek()
        vm.previousWeek()
        assertEquals(TEST_TODAY.minusDays(7), vm.uiState.value.selectedDay)
    }

    @Test
    fun `el filtro se pone al tocar y se quita al volver a tocar`() = runTest {
        tasks.save(task(1, energy = EnergyLevel.LOW))
        tasks.save(task(2, energy = EnergyLevel.HIGH))
        tasks.save(task(3, energy = EnergyLevel.LOW, type = TaskType.ROUTINE))
        val vm = viewModel().also { observe(it) }

        vm.toggleEnergyFilter(EnergyLevel.LOW)
        assertEquals(EnergyLevel.LOW, vm.uiState.value.filter.energy)
        assertEquals(setOf(1L, 3L), vm.allIds().toSet())

        vm.toggleTypeFilter(TaskType.ROUTINE)
        assertEquals(listOf(3L), vm.allIds())

        vm.toggleEnergyFilter(EnergyLevel.LOW)
        assertNull(vm.uiState.value.filter.energy)
        assertEquals(TaskType.ROUTINE, vm.uiState.value.filter.type)

        vm.clearFilters()
        assertFalse(vm.uiState.value.filter.isActive)
        assertEquals(3, vm.allIds().size)
    }

    @Test
    fun `un filtro sin resultados deja la lista vacia pero se sabe que hay actividades`() = runTest {
        tasks.save(task(1, energy = EnergyLevel.LOW))
        val vm = viewModel().also { observe(it) }

        vm.toggleEnergyFilter(EnergyLevel.HIGH)

        assertTrue(vm.uiState.value.sections.isEmpty())
        assertTrue(vm.uiState.value.hasAnyTask)
    }

    @Test
    fun `marcar una tarea la mueve a Hechas hoy`() = runTest {
        tasks.save(task(1, startsInMinutes = 60))
        val vm = viewModel().also { observe(it) }

        vm.setDone(vm.uiState.value.sections.single().items.single(), done = true)

        assertEquals(TaskStatus.DONE, tasks.items.single().status)
        assertEquals(listOf(1L), vm.ids(TaskSection.DONE_TODAY))
        assertTrue(vm.ids(TaskSection.TODAY).isEmpty())
    }

    @Test
    fun `una rutina se marca para el dia que se esta mirando`() = runTest {
        tasks.save(task(1, type = TaskType.ROUTINE))
        val vm = viewModel().also { observe(it) }
        val yesterday = TEST_TODAY.minusDays(1)

        vm.selectDay(yesterday)
        vm.setDone(vm.uiState.value.sections.single().items.single(), done = true)

        assertEquals(setOf(1L to yesterday), completions.items)
        assertTrue(vm.uiState.value.sections.single().items.single().isDone)

        // Hoy sigue pendiente: la marca era de ayer.
        vm.goToToday()
        assertEquals(listOf(1L), vm.ids(TaskSection.TODAY))
    }

    @Test
    fun `en un dia futuro no se puede marcar nada`() = runTest {
        val vm = viewModel().also { observe(it) }

        assertTrue(vm.uiState.value.canToggleDone)
        vm.selectDay(TEST_TODAY.plusDays(1))
        assertFalse(vm.uiState.value.canToggleDone)
        vm.selectDay(TEST_TODAY.minusDays(1))
        assertTrue(vm.uiState.value.canToggleDone)
    }

    @Test
    fun `una actividad borrada se anuncia y se puede recuperar`() = runTest {
        val original = task(1, title = "Renovar DNI")
        tasks.save(original)
        val vm = viewModel().also { observe(it) }

        DeleteTaskUseCase(tasks, trash, testLedger(completions))(original)
        assertEquals(original, vm.uiState.value.deletedTask)
        assertTrue(vm.uiState.value.sections.isEmpty())

        vm.undoDelete()
        assertNull(vm.uiState.value.deletedTask)
        assertEquals(listOf(1L), vm.ids(TaskSection.SOMEDAY))
    }

    @Test
    fun `cerrar el aviso sin deshacer deja el borrado como definitivo`() = runTest {
        val original = task(1)
        tasks.save(original)
        val vm = viewModel().also { observe(it) }
        DeleteTaskUseCase(tasks, trash, testLedger(completions))(original)

        vm.dismissDeleted(original)

        assertNull(vm.uiState.value.deletedTask)
        assertTrue(tasks.items.isEmpty())
    }

    @Test
    fun `si cambia el dia mientras se miraba hoy, se sigue mirando hoy`() = runTest {
        val clock = MutableClock(Instant.parse("2026-10-10T23:50:00Z"))
        val vm = viewModel(clock).also { observe(it) }

        clock.now = Instant.parse("2026-10-11T00:10:00Z")
        vm.refreshToday()

        assertEquals(TEST_TODAY.plusDays(1), vm.uiState.value.today)
        assertEquals(TEST_TODAY.plusDays(1), vm.uiState.value.selectedDay)
    }

    @Test
    fun `si cambia el dia mientras se miraba otro, ese dia no se toca`() = runTest {
        val clock = MutableClock(Instant.parse("2026-10-10T23:50:00Z"))
        val vm = viewModel(clock).also { observe(it) }
        vm.selectDay(TEST_TODAY.plusDays(3))

        clock.now = Instant.parse("2026-10-11T00:10:00Z")
        vm.refreshToday()

        assertEquals(TEST_TODAY.plusDays(1), vm.uiState.value.today)
        assertEquals(TEST_TODAY.plusDays(3), vm.uiState.value.selectedDay)
    }
}
