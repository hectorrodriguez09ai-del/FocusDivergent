package com.focusdivergent.app.presentation.steps

import androidx.lifecycle.SavedStateHandle
import com.focusdivergent.app.domain.model.TaskStatus
import com.focusdivergent.app.domain.reward.RewardCause
import com.focusdivergent.app.domain.steps.StepRules
import com.focusdivergent.app.domain.steps.StepTemplate
import com.focusdivergent.app.domain.usecase.AddStepsUseCase
import com.focusdivergent.app.domain.usecase.FakeRewards
import com.focusdivergent.app.domain.usecase.FakeStepSuggester
import com.focusdivergent.app.domain.usecase.FakeLlmSettings
import com.focusdivergent.app.domain.usecase.SuggestStepsUseCase
import com.focusdivergent.app.domain.usecase.configuredLlm
import com.focusdivergent.app.domain.ai.SuggestResult
import com.focusdivergent.app.domain.usecase.FakeRoutineCompletions
import com.focusdivergent.app.domain.usecase.FakeStepTemplates
import com.focusdivergent.app.domain.usecase.FakeTaskSteps
import com.focusdivergent.app.domain.usecase.FakeTasks
import com.focusdivergent.app.domain.usecase.SaveStepTemplateUseCase
import com.focusdivergent.app.domain.usecase.SetStepDoneUseCase
import com.focusdivergent.app.domain.usecase.SetTaskDoneUseCase
import com.focusdivergent.app.domain.usecase.TEST_CLOCK
import com.focusdivergent.app.domain.usecase.task
import com.focusdivergent.app.domain.usecase.testLedger
import com.focusdivergent.app.presentation.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StepsViewModelsTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val tasks = FakeTasks(listOf(task(1, title = "Informe")))
    private val steps = FakeTaskSteps()
    private val templates = FakeStepTemplates()
    private val completions = FakeRoutineCompletions()
    private val rewards = FakeRewards()
    private val ledger = testLedger(completions, rewards)

    private var llm: FakeLlmSettings = FakeLlmSettings()
    private val suggester = FakeStepSuggester()

    private fun editor(taskId: Long = 1) = StepsEditorViewModel(
        savedStateHandle = SavedStateHandle(mapOf(StepsEditorViewModel.ARG_TASK_ID to taskId)),
        tasks = tasks,
        steps = steps,
        templates = templates,
        addSteps = AddStepsUseCase(steps),
        setStepDone = SetStepDoneUseCase(steps, ledger),
        saveTemplate = SaveStepTemplateUseCase(templates),
        llm = llm,
        suggestSteps = SuggestStepsUseCase(llm, suggester),
    )

    private fun focus(taskId: Long = 1) = StepFocusViewModel(
        savedStateHandle = SavedStateHandle(mapOf(StepFocusViewModel.ARG_TASK_ID to taskId)),
        tasks = tasks,
        steps = steps,
        setStepDone = SetStepDoneUseCase(steps, ledger),
        setTaskDone = SetTaskDoneUseCase(tasks, completions, TEST_CLOCK, ledger),
    )

    private fun <T> TestScope.observe(flow: kotlinx.coroutines.flow.StateFlow<T>) {
        backgroundScope.launch(mainDispatcher.dispatcher) { flow.collect { } }
    }

    private suspend fun seed(vararg texts: String) = steps.addAll(1, texts.toList())

    // ---- Editor de pasos

    @Test
    fun `el editor enseña el titulo de la tarea y sus pasos en orden`() = runTest {
        seed("Uno", "Dos")
        val vm = editor().also { observe(it.uiState) }

        val state = vm.uiState.value
        assertEquals("Informe", state.taskTitle)
        assertEquals(listOf("Uno", "Dos"), state.steps.map { it.text })
        assertTrue(!state.loading)
    }

    @Test
    fun `anadir un paso lo pone al final y no avisa de nada`() = runTest {
        seed("Uno")
        val vm = editor().also { observe(it.uiState) }

        vm.add("  Dos  ")

        assertEquals(listOf("Uno", "Dos"), vm.uiState.value.steps.map { it.text })
        assertNull(vm.uiState.value.notice)
    }

    @Test
    fun `con el maximo de pasos no se anade ninguno y se explica con calma`() = runTest {
        steps.addAll(1, List(StepRules.MAX_STEPS_PER_TASK) { "Paso $it" })
        val vm = editor().also { observe(it.uiState) }

        vm.add("Uno mas")

        assertEquals(StepRules.MAX_STEPS_PER_TASK, vm.uiState.value.steps.size)
        assertEquals(StepsNotice.StepLimit(StepRules.MAX_STEPS_PER_TASK), vm.uiState.value.notice)
    }

    @Test
    fun `cambiar el texto, subir, bajar y quitar un paso`() = runTest {
        seed("Uno", "Dos", "Tres")
        val vm = editor().also { observe(it.uiState) }
        val (one, two, three) = vm.uiState.value.steps

        vm.rename(one, "  Primero  ")
        vm.move(three, up = true)
        assertEquals(listOf("Primero", "Tres", "Dos"), vm.uiState.value.steps.map { it.text })

        vm.move(vm.uiState.value.steps.first(), up = true)
        assertEquals(listOf("Primero", "Tres", "Dos"), vm.uiState.value.steps.map { it.text })

        vm.delete(two)
        assertEquals(listOf("Primero", "Tres"), vm.uiState.value.steps.map { it.text })
    }

    @Test
    fun `renombrar con un texto vacio no cambia nada`() = runTest {
        seed("Uno")
        val vm = editor().also { observe(it.uiState) }

        vm.rename(vm.uiState.value.steps.single(), "   ")

        assertEquals("Uno", vm.uiState.value.steps.single().text)
    }

    @Test
    fun `marcar un paso en el editor suma su XP y desmarcarlo lo retira`() = runTest {
        seed("Uno")
        val vm = editor().also { observe(it.uiState) }

        vm.setDone(vm.uiState.value.steps.single(), true)
        assertTrue(vm.uiState.value.steps.single().done)
        assertEquals(1, vm.uiState.value.doneCount)
        assertEquals(2, rewards.totalXp())

        vm.setDone(vm.uiState.value.steps.single(), false)
        assertEquals(0, rewards.totalXp())
    }

    @Test
    fun `aplicar una plantilla anade sus pasos al final de lo que ya hay`() = runTest {
        seed("Mio")
        val vm = editor().also { observe(it.uiState) }

        vm.applyTemplate(StepTemplate(name = "Estudiar", steps = listOf("Leer", "Resumir"), builtIn = true))

        assertEquals(listOf("Mio", "Leer", "Resumir"), vm.uiState.value.steps.map { it.text })
        assertEquals(StepsNotice.TemplateApplied, vm.uiState.value.notice)
    }

    @Test
    fun `guardar los pasos como plantilla la deja disponible y avisa`() = runTest {
        seed("Lista", "Ir al super")
        val vm = editor().also { observe(it.uiState) }

        vm.saveAsTemplate(" Hacer la compra ")

        assertEquals(StepsNotice.TemplateSaved("Hacer la compra"), vm.uiState.value.notice)
        assertEquals(listOf("Hacer la compra"), vm.uiState.value.userTemplates.map { it.name })
        assertEquals(listOf("Lista", "Ir al super"), vm.uiState.value.userTemplates.single().steps)
    }

    @Test
    fun `guardar una plantilla sin nombre o sin pasos lo dice sin guardar nada`() = runTest {
        val empty = editor().also { observe(it.uiState) }
        empty.saveAsTemplate("Algo")
        assertEquals(StepsNotice.TemplateNeedsSteps, empty.uiState.value.notice)

        seed("Uno")
        empty.saveAsTemplate("   ")
        assertEquals(StepsNotice.TemplateNeedsName, empty.uiState.value.notice)
        assertTrue(templates.items.isEmpty())
    }

    @Test
    fun `borrar una plantilla propia la quita de la lista y el aviso se puede cerrar`() = runTest {
        seed("Uno")
        val vm = editor().also { observe(it.uiState) }
        vm.saveAsTemplate("Mia")
        val saved = vm.uiState.value.userTemplates.single()

        vm.deleteTemplate(saved)
        vm.dismissNotice()

        assertTrue(vm.uiState.value.userTemplates.isEmpty())
        assertNull(vm.uiState.value.notice)
    }

    // ---- Sugerencias con la IA propia

    @Test
    fun `sin configurar la IA no se ofrece`() = runTest {
        val vm = editor().also { observe(it.uiState) }

        assertTrue(!vm.uiState.value.ai.available)
    }

    @Test
    fun `configurada se ofrece, con el servidor al que iria el titulo`() = runTest {
        llm = configuredLlm()
        val vm = editor().also { observe(it.uiState) }

        assertTrue(vm.uiState.value.ai.available)
        assertEquals("api.example.com", vm.uiState.value.ai.host)
    }

    @Test
    fun `la primera vez pregunta antes de enviar nada`() = runTest {
        llm = configuredLlm(consented = false)
        val vm = editor().also { observe(it.uiState) }

        vm.askAi()

        assertEquals(AiPhase.AskingConsent, vm.uiState.value.ai.phase)
        assertTrue(suggester.calls.isEmpty())
    }

    @Test
    fun `al aceptar se recuerda el permiso y se envia solo el titulo de la tarea`() = runTest {
        llm = configuredLlm(consented = false)
        val vm = editor().also { observe(it.uiState) }
        vm.askAi()

        vm.acceptAiConsent()

        assertEquals(AiPhase.Review(listOf("Uno", "Dos")), vm.uiState.value.ai.phase)
        assertEquals("Informe", suggester.calls.single().taskTitle)
        assertEquals("api.example.com", llm.config.value.consentedHost)

        // La siguiente vez ya no pregunta.
        vm.dismissAi()
        vm.askAi()
        assertEquals(2, suggester.calls.size)
    }

    @Test
    fun `rechazar la pregunta no envia nada y no recuerda nada`() = runTest {
        llm = configuredLlm(consented = false)
        val vm = editor().also { observe(it.uiState) }
        vm.askAi()

        vm.dismissAi()

        assertEquals(AiPhase.Idle, vm.uiState.value.ai.phase)
        assertTrue(suggester.calls.isEmpty())
        assertNull(llm.config.value.consentedHost)
    }

    @Test
    fun `de la propuesta solo se anaden los pasos elegidos y nada se guarda antes`() = runTest {
        llm = configuredLlm()
        suggester.result = SuggestResult.Steps(listOf("A", "B", "C"))
        val vm = editor().also { observe(it.uiState) }
        vm.askAi()
        assertTrue(steps.items.isEmpty())

        vm.addSuggested(listOf("A", "C"))

        assertEquals(listOf("A", "C"), steps.getForTask(1).map { it.text })
        assertEquals(AiPhase.Idle, vm.uiState.value.ai.phase)
    }

    @Test
    fun `descartar la propuesta no anade nada`() = runTest {
        llm = configuredLlm()
        val vm = editor().also { observe(it.uiState) }
        vm.askAi()

        vm.dismissAi()

        assertTrue(steps.items.isEmpty())
        assertEquals(AiPhase.Idle, vm.uiState.value.ai.phase)
    }

    @Test
    fun `cada fallo del servicio se cuenta con su motivo y sin tocar los pasos`() = runTest {
        llm = configuredLlm()
        seed("Mio")
        val vm = editor().also { observe(it.uiState) }

        suggester.result = SuggestResult.NoConnection
        vm.askAi()
        assertEquals(AiPhase.Failed(AiFailure.NO_CONNECTION), vm.uiState.value.ai.phase)

        suggester.result = SuggestResult.Rejected(401)
        vm.askAi()
        assertEquals(AiPhase.Failed(AiFailure.REJECTED), vm.uiState.value.ai.phase)

        suggester.result = SuggestResult.Unreadable
        vm.askAi()
        assertEquals(AiPhase.Failed(AiFailure.UNREADABLE), vm.uiState.value.ai.phase)

        assertEquals(listOf("Mio"), steps.getForTask(1).map { it.text })
    }

    // ---- Un paso por pantalla

    @Test
    fun `se enseña el primer paso pendiente con su lugar entre todos`() = runTest {
        seed("Uno", "Dos", "Tres")
        val vm = focus().also { observe(it.uiState) }

        val state = vm.uiState.value as StepFocusUiState.Current
        assertEquals("Uno", state.step.text)
        assertEquals(1, state.number)
        assertEquals(3, state.total)
        assertEquals("Informe", state.taskTitle)
    }

    @Test
    fun `hecho marca el paso, suma su XP y pasa al siguiente`() = runTest {
        seed("Uno", "Dos")
        val vm = focus().also { observe(it.uiState) }

        vm.done()

        assertTrue(steps.items.first().done)
        assertEquals(listOf(RewardCause.STEP_DONE), rewards.events.map { it.cause })
        assertEquals("Dos", (vm.uiState.value as StepFocusUiState.Current).step.text)
    }

    @Test
    fun `ahora no pasa al siguiente sin marcar nada ni sumar XP`() = runTest {
        seed("Uno", "Dos")
        val vm = focus().also { observe(it.uiState) }

        vm.notNow()

        assertEquals("Dos", (vm.uiState.value as StepFocusUiState.Current).step.text)
        assertTrue(steps.items.none { it.done })
        assertEquals(0, rewards.totalXp())
        // El orden guardado no cambia: apartar un paso no lo mueve.
        assertEquals(listOf("Uno", "Dos"), steps.getForTask(1).map { it.text })
    }

    @Test
    fun `al apartar todos los pasos la sesion acaba con calma y se pueden volver a ver`() = runTest {
        seed("Uno", "Dos")
        val vm = focus().also { observe(it.uiState) }

        vm.notNow()
        vm.notNow()
        assertEquals(StepFocusUiState.Paused("Informe"), vm.uiState.value)

        vm.reviewAgain()
        assertEquals("Uno", (vm.uiState.value as StepFocusUiState.Current).step.text)
    }

    @Test
    fun `con el ultimo paso hecho se ofrece dar la tarea por hecha, sin darla por hecha sola`() = runTest {
        seed("Uno")
        val vm = focus().also { observe(it.uiState) }

        vm.done()

        assertEquals(StepFocusUiState.AllDone("Informe", canFinishTask = true), vm.uiState.value)
        assertEquals(TaskStatus.PENDING, tasks.items.single().status)
    }

    @Test
    fun `dar la tarea por hecha la marca, suma su XP y cierra la pantalla`() = runTest {
        seed("Uno")
        val vm = focus().also { observe(it.uiState) }
        vm.done()

        vm.finishTask()

        assertEquals(TaskStatus.DONE, tasks.items.single().status)
        assertTrue(rewards.events.any { it.cause == RewardCause.TASK_DONE })
        assertEquals(StepFocusEvent.Close, withTimeoutOrNull(100) { vm.events.first() })
    }

    @Test
    fun `una tarea ya hecha no vuelve a ofrecer darla por hecha`() = runTest {
        tasks.updateStatus(1, TaskStatus.DONE, TEST_CLOCK.instant())
        seed("Uno")
        val vm = focus().also { observe(it.uiState) }

        vm.done()

        assertEquals(StepFocusUiState.AllDone("Informe", canFinishTask = false), vm.uiState.value)
    }

    @Test
    fun `una tarea sin pasos lo dice, y una que no existe tambien`() = runTest {
        val empty = focus().also { observe(it.uiState) }
        assertEquals(StepFocusUiState.NoSteps("Informe"), empty.uiState.value)

        val missing = focus(taskId = 404).also { observe(it.uiState) }
        assertEquals(StepFocusUiState.NoSteps(""), missing.uiState.value)
    }

    @Test
    fun `hecho y ahora no sin un paso delante no hacen nada`() = runTest {
        val vm = focus().also { observe(it.uiState) }

        vm.done()
        vm.notNow()

        assertTrue(steps.items.isEmpty())
        assertEquals(0, rewards.totalXp())
    }
}
