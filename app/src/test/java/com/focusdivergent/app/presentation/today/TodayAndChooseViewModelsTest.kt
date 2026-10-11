package com.focusdivergent.app.presentation.today

import com.focusdivergent.app.domain.audio.AmbientAudioState
import com.focusdivergent.app.domain.audio.AmbientSound
import com.focusdivergent.app.domain.usecase.FakeAmbientAudio
import com.focusdivergent.app.domain.usecase.FakeTimerStore
import com.focusdivergent.app.domain.timer.TimerMode
import com.focusdivergent.app.domain.timer.TimerSession
import com.focusdivergent.app.domain.model.EnergyLevel
import com.focusdivergent.app.domain.model.TaskStatus
import com.focusdivergent.app.domain.model.TaskType
import com.focusdivergent.app.domain.usecase.FakeCosmetics
import com.focusdivergent.app.domain.usecase.FakeDailyEnergyStore
import com.focusdivergent.app.domain.usecase.FakeMascotSettings
import com.focusdivergent.app.domain.usecase.FakeRewards
import com.focusdivergent.app.domain.usecase.FakeUserStats
import com.focusdivergent.app.domain.usecase.ObserveProgressUseCase
import com.focusdivergent.app.domain.usecase.FakeRoutineCompletions
import com.focusdivergent.app.domain.usecase.FakeTaskSteps
import com.focusdivergent.app.domain.usecase.FakeTasks
import com.focusdivergent.app.domain.usecase.testLedger
import com.focusdivergent.app.domain.usecase.SetTaskDoneUseCase
import com.focusdivergent.app.domain.usecase.StartTaskUseCase
import com.focusdivergent.app.domain.usecase.TEST_CLOCK
import com.focusdivergent.app.domain.usecase.TEST_NOW
import com.focusdivergent.app.domain.usecase.TEST_TODAY
import com.focusdivergent.app.domain.usecase.task
import com.focusdivergent.app.presentation.MainDispatcherRule
import com.focusdivergent.app.presentation.chooseforme.ChooseForMeEvent
import com.focusdivergent.app.presentation.chooseforme.ChooseForMeViewModel
import com.focusdivergent.app.presentation.chooseforme.ChooseStep
import kotlin.random.Random
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TodayAndChooseViewModelsTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val day = 24 * 60L
    private val tasks = FakeTasks()
    private val completions = FakeRoutineCompletions()

    private fun setDone() = SetTaskDoneUseCase(tasks, completions, TEST_CLOCK, testLedger(completions))
    private val timer = FakeTimerStore()
    private val steps = FakeTaskSteps()
    private val ambient = FakeAmbientAudio()
    private val rewards = FakeRewards()

    private fun progress() = ObserveProgressUseCase(rewards, completions, FakeCosmetics(), FakeUserStats(), FakeMascotSettings())

    // ---- Hoy

    private fun today(energy: FakeDailyEnergyStore = FakeDailyEnergyStore()) =
        TodayViewModel(tasks, completions, steps, energy, setDone(), timer, ambient, progress(), TEST_CLOCK)

    private fun TestScope.observe(vm: TodayViewModel) {
        backgroundScope.launch(mainDispatcher.dispatcher) { vm.uiState.collect() }
    }

    @Test
    fun `sin ninguna actividad Hoy lo sabe, para invitar a crear la primera`() = runTest {
        val vm = today().also { observe(it) }

        val state = vm.uiState.value
        assertFalse(state.hasAnyTask)
        assertFalse(state.loading)
        assertEquals(TEST_TODAY, state.today)
        assertNull(state.energy)
        assertTrue(state.todayItems.isEmpty())
    }

    @Test
    fun `Hoy sabe si hay una sesion del temporizador en curso`() = runTest {
        val vm = today().also { observe(it) }
        assertFalse(vm.uiState.value.timerActive)

        timer.save(TimerSession(TimerMode.Single(25), null, null, TEST_NOW))
        assertTrue(vm.uiState.value.timerActive)

        timer.clear()
        assertFalse(vm.uiState.value.timerActive)
    }

    @Test
    fun `Hoy sabe si hay un sonido ambiente sonando o en pausa`() = runTest {
        val vm = today().also { observe(it) }
        assertFalse(vm.uiState.value.audioActive)

        ambient.setState(AmbientAudioState(sound = AmbientSound.RAIN, playing = true))
        assertTrue(vm.uiState.value.audioActive)
        assertTrue(vm.uiState.value.audioPlaying)

        ambient.setState(AmbientAudioState(sound = AmbientSound.RAIN, playing = false))
        assertTrue(vm.uiState.value.audioActive)
        assertFalse(vm.uiState.value.audioPlaying)

        ambient.setState(AmbientAudioState())
        assertFalse(vm.uiState.value.audioActive)
    }

    @Test
    fun `solo se enseñan tres actividades de hoy y se sabe cuantas hay en total`() = runTest {
        repeat(5) { tasks.save(task(it + 1L, startsInMinutes = 60L + it)) }
        val vm = today().also { observe(it) }

        val state = vm.uiState.value

        assertEquals(TodayViewModel.MAX_TODAY_ITEMS, state.todayItems.size)
        assertEquals(5, state.todayPending)
        assertTrue(state.hasMoreToday)
        assertEquals(listOf(1L, 2L, 3L), state.todayItems.map { it.task.id })
    }

    @Test
    fun `con tres o menos no hay Ver todas`() = runTest {
        repeat(3) { tasks.save(task(it + 1L, startsInMinutes = 60L)) }
        val vm = today().also { observe(it) }

        assertFalse(vm.uiState.value.hasMoreToday)
    }

    @Test
    fun `lo de otros dias y lo de mas adelante no entra en Para hoy`() = runTest {
        tasks.save(task(1, startsInMinutes = 2 * day))
        tasks.save(task(2))
        val vm = today().also { observe(it) }

        assertTrue(vm.uiState.value.todayItems.isEmpty())
        assertTrue(vm.uiState.value.hasAnyTask)
    }

    @Test
    fun `la proxima obligacion tiene su tarjeta y no se repite en la lista`() = runTest {
        tasks.save(task(1, title = "Dentista", type = TaskType.OBLIGATION, startsInMinutes = 90))
        tasks.save(task(2, title = "Tender", startsInMinutes = 30))
        val vm = today().also { observe(it) }

        val state = vm.uiState.value

        assertEquals("Dentista", state.nextObligation?.task?.title)
        assertEquals(listOf(2L), state.todayItems.map { it.task.id })
    }

    @Test
    fun `una obligacion que ya empezo no es la proxima`() = runTest {
        tasks.save(task(1, type = TaskType.OBLIGATION, startsInMinutes = -30))
        tasks.save(task(2, title = "Cita siguiente", type = TaskType.OBLIGATION, startsInMinutes = 120))
        val vm = today().also { observe(it) }

        assertEquals("Cita siguiente", vm.uiState.value.nextObligation?.task?.title)
    }

    @Test
    fun `una tarea en marcha va a Ahora y no se repite en Para hoy`() = runTest {
        tasks.save(task(1, title = "Informe", status = TaskStatus.IN_PROGRESS, startsInMinutes = 30))
        tasks.save(task(2, title = "Otra", startsInMinutes = 30))
        val vm = today().also { observe(it) }

        val state = vm.uiState.value

        assertEquals(listOf("Informe"), state.nowItems.map { it.task.title })
        assertEquals(listOf("Otra"), state.todayItems.map { it.task.title })
        assertEquals(1, state.todayPending)
    }

    @Test
    fun `Ahora lleva el siguiente paso de las tareas en marcha que tienen pasos`() = runTest {
        tasks.save(task(1, title = "Informe", status = TaskStatus.IN_PROGRESS))
        tasks.save(task(2, title = "Sin pasos", status = TaskStatus.IN_PROGRESS))
        steps.addAll(1, listOf("Abrir el documento", "Escribir el titulo"))
        steps.setDone(steps.items.first().id, true)
        val vm = today().also { observe(it) }

        val progress = vm.uiState.value.nowSteps.getValue(1)

        assertEquals("Escribir el titulo", progress.next?.text)
        assertEquals(1, progress.done)
        assertEquals(2, progress.total)
        assertEquals(null, vm.uiState.value.nowSteps[2])
    }

    @Test
    fun `una tarea en marcha sin fecha tambien sale en Ahora`() = runTest {
        tasks.save(task(1, status = TaskStatus.IN_PROGRESS))
        val vm = today().also { observe(it) }

        assertEquals(listOf(1L), vm.uiState.value.nowItems.map { it.task.id })
    }

    @Test
    fun `lo hecho hoy se cuenta`() = runTest {
        tasks.save(task(1, status = TaskStatus.DONE, completedAt = TEST_NOW))
        tasks.save(task(2, status = TaskStatus.DONE, completedAt = TEST_NOW.minusSeconds(30 * 3600)))
        tasks.save(task(3, type = TaskType.ROUTINE))
        completions.markCompleted(3, TEST_TODAY, TEST_NOW)
        val vm = today().also { observe(it) }

        assertEquals(2, vm.uiState.value.doneToday)
    }

    @Test
    fun `elegir energia la guarda para todo el dia`() = runTest {
        val store = FakeDailyEnergyStore()
        val vm = today(store).also { observe(it) }

        vm.setEnergy(EnergyLevel.LOW)

        assertEquals(EnergyLevel.LOW, vm.uiState.value.energy)
        assertEquals(EnergyLevel.LOW, store.get(TEST_TODAY))
    }

    @Test
    fun `la energia de ayer no cuenta hoy`() = runTest {
        val vm = today(FakeDailyEnergyStore(TEST_TODAY.minusDays(1) to EnergyLevel.HIGH)).also { observe(it) }

        assertNull(vm.uiState.value.energy)
    }

    @Test
    fun `marcar hecha una actividad la saca de la lista y la cuenta`() = runTest {
        tasks.save(task(1, startsInMinutes = 30))
        val vm = today().also { observe(it) }

        vm.setDone(vm.uiState.value.todayItems.single(), done = true)

        assertTrue(vm.uiState.value.todayItems.isEmpty())
        assertEquals(1, vm.uiState.value.doneToday)
    }

    @Test
    fun `dejar para luego devuelve una tarea en marcha a pendiente`() = runTest {
        tasks.save(task(1, status = TaskStatus.IN_PROGRESS, startsInMinutes = 30))
        val vm = today().also { observe(it) }

        vm.leaveForLater(vm.uiState.value.nowItems.single())

        assertTrue(vm.uiState.value.nowItems.isEmpty())
        assertEquals(TaskStatus.PENDING, tasks.items.single().status)
        assertEquals(listOf(1L), vm.uiState.value.todayItems.map { it.task.id })
    }

    // ---- Elige por mí

    private fun choose(store: FakeDailyEnergyStore = FakeDailyEnergyStore(), seed: Int = 7) = ChooseForMeViewModel(
        tasks = tasks,
        completions = completions,
        energyStore = store,
        startTask = StartTaskUseCase(tasks),
        clock = TEST_CLOCK,
        random = Random(seed),
    )

    private suspend fun ChooseForMeViewModel.nextEvent() = withTimeoutOrNull(100) { events.first() }

    @Test
    fun `si hoy no ha dicho su energia, la primera pregunta es la energia`() {
        val state = choose().uiState.value

        assertEquals(ChooseStep.ENERGY, state.step)
        assertNull(state.energy)
    }

    @Test
    fun `si ya la dijo hoy se salta esa pregunta`() {
        val state = choose(FakeDailyEnergyStore(TEST_TODAY to EnergyLevel.MEDIUM)).uiState.value

        assertEquals(ChooseStep.TIME, state.step)
        assertEquals(EnergyLevel.MEDIUM, state.energy)
    }

    @Test
    fun `la energia de ayer no vale, se vuelve a preguntar`() {
        val state = choose(FakeDailyEnergyStore(TEST_TODAY.minusDays(1) to EnergyLevel.HIGH)).uiState.value

        assertEquals(ChooseStep.ENERGY, state.step)
    }

    @Test
    fun `decir la energia la guarda y pasa a preguntar el tiempo`() {
        val store = FakeDailyEnergyStore()
        val vm = choose(store)

        vm.setEnergy(EnergyLevel.LOW)

        assertEquals(ChooseStep.TIME, vm.uiState.value.step)
        assertEquals(EnergyLevel.LOW, store.get(TEST_TODAY))
    }

    @Test
    fun `se propone una sola actividad que respeta la energia y el tiempo`() = runTest {
        tasks.save(task(1, title = "Larga y pesada", energy = EnergyLevel.HIGH, estimatedMinutes = 90))
        tasks.save(task(2, title = "Corta y ligera", energy = EnergyLevel.LOW, estimatedMinutes = 10))
        tasks.save(task(3, title = "Corta, de energia alta", energy = EnergyLevel.HIGH, estimatedMinutes = 10))
        val vm = choose(FakeDailyEnergyStore(TEST_TODAY to EnergyLevel.LOW))

        vm.chooseTime(15)

        val state = vm.uiState.value
        assertEquals(ChooseStep.RESULT, state.step)
        assertEquals("Corta y ligera", state.pick?.title)
        assertFalse("no hay alternativas", state.canAskAnother)
    }

    @Test
    fun `si nada encaja se dice y se puede volver a probar con otro tiempo`() = runTest {
        tasks.save(task(1, energy = EnergyLevel.MEDIUM, estimatedMinutes = 90))
        val vm = choose(FakeDailyEnergyStore(TEST_TODAY to EnergyLevel.HIGH))

        vm.chooseTime(5)
        assertEquals(ChooseStep.NONE_FOUND, vm.uiState.value.step)
        assertNull(vm.uiState.value.pick)

        vm.backToTime()
        assertEquals(ChooseStep.TIME, vm.uiState.value.step)
        vm.chooseTime(null)
        assertEquals(ChooseStep.RESULT, vm.uiState.value.step)
        assertEquals(1L, vm.uiState.value.pick?.id)
    }

    @Test
    fun `sin ninguna actividad no hay propuesta`() = runTest {
        val vm = choose(FakeDailyEnergyStore(TEST_TODAY to EnergyLevel.HIGH))

        vm.chooseTime(null)

        assertEquals(ChooseStep.NONE_FOUND, vm.uiState.value.step)
    }

    @Test
    fun `Otra se puede pedir dos veces y nunca repite, y luego se convierte en Ver mi lista`() = runTest {
        repeat(6) { tasks.save(task(it + 1L)) }
        val vm = choose(FakeDailyEnergyStore(TEST_TODAY to EnergyLevel.HIGH))

        vm.chooseTime(null)
        val shown = mutableListOf(vm.uiState.value.pick!!.id)
        assertTrue(vm.uiState.value.canAskAnother)

        vm.another()
        shown += vm.uiState.value.pick!!.id
        assertTrue("queda un cambio más", vm.uiState.value.canAskAnother)

        vm.another()
        shown += vm.uiState.value.pick!!.id
        assertFalse("tres propuestas como mucho", vm.uiState.value.canAskAnother)

        assertEquals("ninguna se repite", 3, shown.toSet().size)

        // Con el límite alcanzado, pedir otra no hace nada.
        vm.another()
        assertEquals(shown.last(), vm.uiState.value.pick!!.id)
    }

    @Test
    fun `con pocas alternativas Otra se agota antes del limite`() = runTest {
        tasks.save(task(1))
        tasks.save(task(2))
        val vm = choose(FakeDailyEnergyStore(TEST_TODAY to EnergyLevel.HIGH))

        vm.chooseTime(null)
        assertTrue(vm.uiState.value.canAskAnother)
        vm.another()

        assertFalse(vm.uiState.value.canAskAnother)
    }

    @Test
    fun `elegir otro tiempo reinicia la ronda y vuelve a poder proponerse lo ya visto`() = runTest {
        tasks.save(task(1))
        val vm = choose(FakeDailyEnergyStore(TEST_TODAY to EnergyLevel.HIGH))
        vm.chooseTime(null)
        val first = vm.uiState.value.pick!!.id

        vm.backToTime()
        vm.chooseTime(30)

        assertEquals(first, vm.uiState.value.pick?.id)
    }

    @Test
    fun `empezar deja la tarea en marcha y avisa para volver a Hoy`() = runTest {
        tasks.save(task(1, title = "Informe"))
        val vm = choose(FakeDailyEnergyStore(TEST_TODAY to EnergyLevel.HIGH))
        vm.chooseTime(null)

        vm.start()

        assertEquals(TaskStatus.IN_PROGRESS, tasks.items.single().status)
        assertEquals(ChooseForMeEvent.Started, vm.nextEvent())
    }

    @Test
    fun `empezar una rutina no cambia su estado, que se marca por dia`() = runTest {
        tasks.save(task(1, type = TaskType.ROUTINE, recurrenceRule = "FREQ=DAILY"))
        val vm = choose(FakeDailyEnergyStore(TEST_TODAY to EnergyLevel.HIGH))
        vm.chooseTime(null)

        vm.start()

        assertEquals(TaskStatus.PENDING, tasks.items.single().status)
        assertEquals(ChooseForMeEvent.Started, vm.nextEvent())
    }

    @Test
    fun `una tarea ya empezada no se vuelve a proponer`() = runTest {
        tasks.save(task(1, status = TaskStatus.IN_PROGRESS))
        val vm = choose(FakeDailyEnergyStore(TEST_TODAY to EnergyLevel.HIGH))

        vm.chooseTime(null)

        assertEquals(ChooseStep.NONE_FOUND, vm.uiState.value.step)
    }

    @Test
    fun `una rutina ya hecha hoy no se propone`() = runTest {
        tasks.save(task(1, type = TaskType.ROUTINE, recurrenceRule = "FREQ=DAILY"))
        completions.markCompleted(1, TEST_TODAY, TEST_NOW)
        val vm = choose(FakeDailyEnergyStore(TEST_TODAY to EnergyLevel.HIGH))

        vm.chooseTime(null)

        assertEquals(ChooseStep.NONE_FOUND, vm.uiState.value.step)
    }

    @Test
    fun `cambiar la energia vuelve a esa pregunta sin perder lo demas`() = runTest {
        val vm = choose(FakeDailyEnergyStore(TEST_TODAY to EnergyLevel.MEDIUM))

        vm.changeEnergy()

        assertEquals(ChooseStep.ENERGY, vm.uiState.value.step)
        assertEquals(EnergyLevel.MEDIUM, vm.uiState.value.energy)
    }

    @Test
    fun `ver la lista y crear una tarea avisan por eventos`() = runTest {
        val vm = choose(FakeDailyEnergyStore(TEST_TODAY to EnergyLevel.MEDIUM))

        vm.seeList()
        assertEquals(ChooseForMeEvent.SeeList, vm.nextEvent())

        vm.createTask()
        assertEquals(ChooseForMeEvent.CreateTask, vm.nextEvent())
    }

    @Test
    fun `empezar sin propuesta no hace nada`() = runTest {
        val vm = choose(FakeDailyEnergyStore(TEST_TODAY to EnergyLevel.MEDIUM))

        vm.start()

        assertNull(vm.nextEvent())
        assertNotNull(vm.uiState.value)
    }
}
