package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.reward.RewardCause
import com.focusdivergent.app.domain.reward.RewardEvent
import com.focusdivergent.app.domain.reward.RewardRules
import com.focusdivergent.app.domain.steps.StepProgress
import com.focusdivergent.app.domain.steps.StepQueue
import com.focusdivergent.app.domain.steps.StepRules
import com.focusdivergent.app.domain.steps.StepTemplate
import com.focusdivergent.app.domain.steps.TaskStep
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Las reglas de los micro-pasos (RF-3.1): limpieza del texto, orden, XP por paso y plantillas. */
class StepUseCasesTest {

    private val steps = FakeTaskSteps()
    private val templates = FakeStepTemplates()
    private val rewards = FakeRewards()
    private val ledger = testLedger(rewards = rewards)

    private fun step(id: Long, position: Int, done: Boolean = false, task: Long = 1) =
        TaskStep(id = id, taskId = task, position = position, text = "Paso $id", done = done)

    // ---- Texto

    @Test
    fun `el texto de un paso se limpia de espacios y saltos, y uno vacio se descarta`() {
        assertEquals("Abrir el documento", StepRules.cleanStep("  Abrir \n el   documento  "))
        assertNull(StepRules.cleanStep("   \n  "))
        assertEquals(StepRules.MAX_STEP_LENGTH, StepRules.cleanStep("a".repeat(500))!!.length)
        assertEquals("Hacer la compra", StepRules.cleanName(" Hacer   la compra "))
        assertNull(StepRules.cleanName(""))
    }

    // ---- Qué toca ahora

    @Test
    fun `el progreso cuenta los hechos y senala el primero pendiente por orden`() {
        val progress = StepProgress.of(listOf(step(1, 0, done = true), step(3, 2), step(2, 1)))

        assertEquals(1, progress.done)
        assertEquals(3, progress.total)
        assertEquals(2L, progress.next?.id)
        assertEquals(StepProgress.NONE, StepProgress.of(emptyList()))
    }

    @Test
    fun `con todos hechos no queda siguiente paso`() {
        val progress = StepProgress.of(listOf(step(1, 0, done = true), step(2, 1, done = true)))

        assertTrue(progress.allDone)
        assertNull(progress.next)
    }

    @Test
    fun `ahora no aparta el paso sin tocar el orden y pasa al siguiente`() {
        val list = listOf(step(1, 0), step(2, 1), step(3, 2))

        assertEquals(1L, StepQueue.current(list, emptySet())?.id)
        assertEquals(2L, StepQueue.current(list, setOf(1L))?.id)
        assertEquals(3L, StepQueue.current(list, setOf(1L, 2L))?.id)
        assertNull(StepQueue.current(list, setOf(1L, 2L, 3L)))
    }

    @Test
    fun `los pasos hechos no vuelven a tocar`() {
        val list = listOf(step(1, 0, done = true), step(2, 1))

        assertEquals(2L, StepQueue.current(list, emptySet())?.id)
    }

    // ---- Añadir

    @Test
    fun `anadir pasos los limpia, descarta los vacios y los pone al final`() = runTest {
        val add = AddStepsUseCase(steps)
        steps.addAll(1, listOf("Primero"))

        val added = add(1, listOf("  Segundo  ", "", "   ", "Tercero"))

        assertEquals(2, added)
        assertEquals(listOf("Primero", "Segundo", "Tercero"), steps.getForTask(1).map { it.text })
    }

    @Test
    fun `no se pasa del maximo de pasos por tarea y lo que sobra se deja fuera`() = runTest {
        val add = AddStepsUseCase(steps)
        steps.addAll(1, List(StepRules.MAX_STEPS_PER_TASK - 1) { "Paso $it" })

        val added = add(1, listOf("Cabe", "No cabe"))

        assertEquals(1, added)
        assertEquals(StepRules.MAX_STEPS_PER_TASK, steps.getForTask(1).size)
        assertEquals(0, add(1, listOf("Otro")))
    }

    // ---- XP

    @Test
    fun `hacer un paso suma 2 XP y ninguna moneda, y desmarcarlo lo retira`() = runTest {
        steps.addAll(1, listOf("Uno"))
        val one = steps.getForTask(1).single()
        val setDone = SetStepDoneUseCase(steps, ledger)

        setDone(one, done = true)

        assertEquals(listOf(RewardEvent(RewardCause.STEP_DONE, one.id, TEST_TODAY, xp = 2, coins = 0)), rewards.events)
        assertTrue(steps.getById(one.id)!!.done)

        setDone(one, done = false)

        assertEquals(0, rewards.totalXp())
        assertTrue(!steps.getById(one.id)!!.done)
    }

    @Test
    fun `marcar el mismo paso dos veces no suma dos veces`() = runTest {
        steps.addAll(1, listOf("Uno"))
        val one = steps.getForTask(1).single()
        val setDone = SetStepDoneUseCase(steps, ledger)

        setDone(one, done = true)
        setDone(one, done = true)

        assertEquals(RewardRules.STEP_XP, rewards.totalXp())
    }

    // ---- Plantillas

    @Test
    fun `guardar una plantilla limpia el nombre y los pasos`() = runTest {
        val save = SaveStepTemplateUseCase(templates)

        val result = save("  Hacer la   compra ", listOf(" Lista ", "", "Ir al super"))

        assertEquals(SaveTemplateResult.Saved, result)
        assertEquals(listOf("Hacer la compra"), templates.items.map { it.name })
        assertEquals(listOf("Lista", "Ir al super"), templates.items.single().steps)
    }

    @Test
    fun `una plantilla necesita nombre y al menos un paso`() = runTest {
        val save = SaveStepTemplateUseCase(templates)

        assertEquals(SaveTemplateResult.NeedsName, save("  ", listOf("Uno")))
        assertEquals(SaveTemplateResult.NeedsSteps, save("Informe", listOf("", " ")))
        assertTrue(templates.items.isEmpty())
    }

    @Test
    fun `guardar con un nombre que ya existe la sustituye, aunque cambien las mayusculas`() = runTest {
        val save = SaveStepTemplateUseCase(templates)
        save("Informe", listOf("Uno"))

        save("INFORME", listOf("Uno", "Dos"))

        assertEquals(1, templates.items.size)
        assertEquals(listOf("Uno", "Dos"), templates.items.single().steps)
    }

    @Test
    fun `hay un maximo de plantillas propias, pero sustituir una existente siempre se puede`() = runTest {
        val full = FakeStepTemplates(List(StepRules.MAX_USER_TEMPLATES) { StepTemplate(id = it + 1L, name = "P$it", steps = listOf("x")) })
        val save = SaveStepTemplateUseCase(full)

        assertEquals(SaveTemplateResult.TooMany, save("Nueva", listOf("Uno")))
        assertEquals(SaveTemplateResult.Saved, save("p3", listOf("Otro")))
        assertEquals(StepRules.MAX_USER_TEMPLATES, full.items.size)
    }
}
