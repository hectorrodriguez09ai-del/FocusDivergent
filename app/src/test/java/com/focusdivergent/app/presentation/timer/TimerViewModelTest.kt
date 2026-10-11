package com.focusdivergent.app.presentation.timer

import androidx.lifecycle.SavedStateHandle
import com.focusdivergent.app.domain.model.TaskStatus
import com.focusdivergent.app.domain.timer.TimerMode
import com.focusdivergent.app.domain.timer.TimerPhaseKind
import com.focusdivergent.app.domain.timer.TimerState
import com.focusdivergent.app.domain.usecase.FakeRoutineCompletions
import com.focusdivergent.app.domain.usecase.FakeTaskSteps
import com.focusdivergent.app.domain.usecase.FakeTasks
import com.focusdivergent.app.domain.usecase.testLedger
import com.focusdivergent.app.domain.usecase.FakeTimerAlarms
import com.focusdivergent.app.domain.usecase.FakeTimerNotifier
import com.focusdivergent.app.domain.usecase.FakeTimerSettings
import com.focusdivergent.app.domain.usecase.FakeTimerStore
import com.focusdivergent.app.domain.usecase.FinishTimerUseCase
import com.focusdivergent.app.domain.usecase.MutableClock
import com.focusdivergent.app.domain.usecase.SetTaskDoneUseCase
import com.focusdivergent.app.domain.usecase.StartTaskUseCase
import com.focusdivergent.app.domain.usecase.StartTimerUseCase
import com.focusdivergent.app.domain.usecase.StopTimerUseCase
import com.focusdivergent.app.domain.usecase.SyncTimerUseCase
import com.focusdivergent.app.domain.usecase.task
import com.focusdivergent.app.presentation.MainDispatcherRule
import java.time.Duration
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TimerViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val clock = MutableClock()
    private val store = FakeTimerStore()
    private val settings = FakeTimerSettings()
    private val alarms = FakeTimerAlarms()
    private val notifier = FakeTimerNotifier()
    private val tasks = FakeTasks()
    private val steps = FakeTaskSteps()
    private val completions = FakeRoutineCompletions()
    private val ledger = testLedger(completions, clock = clock)

    private fun viewModel(taskId: Long = 0): TimerViewModel {
        val sync = SyncTimerUseCase(store, notifier, alarms, clock)
        return TimerViewModel(
            savedStateHandle = SavedStateHandle(mapOf(TimerViewModel.ARG_TASK_ID to taskId)),
            tasks = tasks,
            taskSteps = steps,
            store = store,
            settings = settings,
            startTimer = StartTimerUseCase(store, settings, StartTaskUseCase(tasks), sync, clock, ledger),
            stopTimer = StopTimerUseCase(store, sync, ledger),
            finishTimer = FinishTimerUseCase(store, tasks, SetTaskDoneUseCase(tasks, completions, clock, ledger), sync, ledger),
            clock = clock,
        )
    }

    private fun TestScope.observe(vm: TimerViewModel) {
        backgroundScope.launch(mainDispatcher.dispatcher) { vm.uiState.collect() }
    }

    @Test
    fun `arranca con lo ultimo que se eligio y sin sesion`() = runTest {
        settings.single = TimerMode.Single(45)
        settings.pomodoro = TimerMode.Pomodoro(50, 10, 4)
        val vm = viewModel().also { observe(it) }

        val state = vm.uiState.value

        assertEquals(TimerKind.SINGLE, state.kind)
        assertEquals(TimerMode.Single(45), state.single)
        assertEquals(TimerMode.Pomodoro(50, 10, 4), state.pomodoro)
        assertNull(state.session)
        assertNull(state.state)
    }

    @Test
    fun `cambiar de modo y de valores cambia lo que se empezaria`() = runTest {
        val vm = viewModel().also { observe(it) }

        vm.setSingleMinutes(15)
        assertEquals(TimerMode.Single(15), vm.uiState.value.selectedMode)

        vm.setKind(TimerKind.POMODORO)
        vm.setFocusMinutes(30)
        vm.setBreakMinutes(10)
        vm.setCycles(4)
        assertEquals(TimerMode.Pomodoro(30, 10, 4), vm.uiState.value.selectedMode)
    }

    @Test
    fun `empezar crea la sesion y la pantalla pasa a enseñar la cuenta atras`() = runTest {
        val vm = viewModel().also { observe(it) }
        vm.setSingleMinutes(25)

        vm.start()

        val state = vm.uiState.value
        assertEquals(TimerMode.Single(25), state.session?.mode)
        val running = state.state as TimerState.Running
        assertEquals(Duration.ofMinutes(25), running.remaining)
        assertEquals(TimerPhaseKind.FOCUS, running.phase.kind)
        assertEquals(clock.now.plus(Duration.ofMinutes(25)), alarms.scheduled)
    }

    @Test
    fun `mientras corre, la cuenta atras baja con el tiempo`() = runTest {
        val vm = viewModel().also { observe(it) }
        vm.start()

        clock.now = clock.now.plus(Duration.ofMinutes(5))
        advanceTimeBy(1_100)

        val running = vm.uiState.value.state as TimerState.Running
        assertEquals(Duration.ofMinutes(20), running.remaining)
    }

    @Test
    fun `al pasar todo el tiempo la pantalla pasa a sesion terminada`() = runTest {
        val vm = viewModel().also { observe(it) }
        vm.start()

        clock.now = clock.now.plus(Duration.ofMinutes(26))
        advanceTimeBy(1_100)

        assertTrue(vm.uiState.value.state is TimerState.Finished)
        assertTrue("la sesión espera a que se cierre", vm.uiState.value.session != null)
    }

    @Test
    fun `desde una tarea, la sesion la acompaña y la deja en marcha`() = runTest {
        tasks.save(task(4, title = "Informe"))
        val vm = viewModel(taskId = 4).also { observe(it) }

        assertEquals("Informe", vm.uiState.value.task?.title)
        vm.start()

        assertEquals("Informe", vm.uiState.value.session?.taskTitle)
        assertEquals(TaskStatus.IN_PROGRESS, tasks.getById(4)?.status)
    }

    @Test
    fun `parar borra la sesion y vuelve a la eleccion`() = runTest {
        val vm = viewModel().also { observe(it) }
        vm.start()

        vm.stop()

        assertNull(vm.uiState.value.session)
        assertNull(vm.uiState.value.state)
        assertNull(alarms.scheduled)
    }

    @Test
    fun `el temporizador de una tarea con pasos enseña el paso que toca, antes y durante la sesion`() = runTest {
        tasks.save(task(4, title = "Informe"))
        steps.addAll(4, listOf("Abrir el documento", "Escribir el titulo"))
        steps.setDone(steps.items.first().id, true)
        val vm = viewModel(taskId = 4).also { observe(it) }

        assertEquals("Escribir el titulo", vm.uiState.value.steps.next?.text)

        vm.start()
        assertEquals("Escribir el titulo", vm.uiState.value.steps.next?.text)

        steps.setDone(steps.items.last().id, true)
        assertNull(vm.uiState.value.steps.next)
    }

    @Test
    fun `un temporizador suelto no tiene pasos`() = runTest {
        steps.addAll(4, listOf("De otra tarea"))
        val vm = viewModel().also { observe(it) }

        assertFalse(vm.uiState.value.steps.hasSteps)
    }

    @Test
    fun `cerrar la sesion con la tarea hecha la marca y cierra la pantalla`() = runTest {
        tasks.save(task(4, title = "Informe"))
        val vm = viewModel(taskId = 4).also { observe(it) }
        vm.start()
        clock.now = clock.now.plus(Duration.ofMinutes(30))

        vm.finish(taskDone = true)

        assertEquals(TaskStatus.DONE, tasks.getById(4)?.status)
        assertEquals(TimerEvent.Close, withTimeoutOrNull(100) { vm.events.first() })
        assertNull(vm.uiState.value.session)
    }

    @Test
    fun `cerrar con todavia no deja la tarea como estaba`() = runTest {
        tasks.save(task(4, title = "Informe"))
        val vm = viewModel(taskId = 4).also { observe(it) }
        vm.start()

        vm.finish(taskDone = false)

        assertEquals(TaskStatus.IN_PROGRESS, tasks.getById(4)?.status)
        assertFalse(store.session.value != null)
    }
}
