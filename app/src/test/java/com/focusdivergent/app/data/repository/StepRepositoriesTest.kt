package com.focusdivergent.app.data.repository

import android.app.Application
import com.focusdivergent.app.data.local.FocusDatabase
import com.focusdivergent.app.domain.model.EnergyLevel
import com.focusdivergent.app.domain.model.Task
import com.focusdivergent.app.domain.model.TaskType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class StepRepositoriesTest {

    private lateinit var db: FocusDatabase
    private lateinit var tasks: TaskRepositoryImpl
    private lateinit var steps: TaskStepRepositoryImpl
    private lateinit var templates: StepTemplateRepositoryImpl

    @Before
    fun setUp() {
        db = createTestDatabase()
        tasks = TaskRepositoryImpl(db.taskDao())
        steps = TaskStepRepositoryImpl(db.taskStepDao())
        templates = StepTemplateRepositoryImpl(db.stepTemplateDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun newTask(title: String = "Informe") =
        tasks.save(Task(title = title, type = TaskType.TASK, energyLevel = EnergyLevel.MEDIUM))

    @Test
    fun `los pasos se anaden al final y se leen por orden`() = runTest {
        val task = newTask()

        steps.addAll(task, listOf("Uno", "Dos"))
        steps.addAll(task, listOf("Tres"))

        assertEquals(listOf("Uno", "Dos", "Tres"), steps.getForTask(task).map { it.text })
        assertEquals(listOf(0, 1, 2), steps.getForTask(task).map { it.position })
        assertTrue(steps.getForTask(task).none { it.done })
    }

    @Test
    fun `cada tarea tiene sus pasos y observeAll los trae todos`() = runTest {
        val a = newTask("A")
        val b = newTask("B")
        steps.addAll(a, listOf("a1"))
        steps.addAll(b, listOf("b1", "b2"))

        assertEquals(listOf("a1"), steps.observeForTask(a).first().map { it.text })
        assertEquals(listOf("b1", "b2"), steps.observeForTask(b).first().map { it.text })
        assertEquals(3, steps.observeAll().first().size)
    }

    @Test
    fun `marcar y renombrar un paso solo cambia ese paso`() = runTest {
        val task = newTask()
        val (one, two) = steps.addAll(task, listOf("Uno", "Dos"))

        steps.setDone(one, true)
        steps.rename(two, "Segundo")

        val list = steps.getForTask(task)
        assertEquals(listOf(true, false), list.map { it.done })
        assertEquals(listOf("Uno", "Segundo"), list.map { it.text })
        assertEquals("Segundo", steps.getById(two)?.text)
    }

    @Test
    fun `subir y bajar intercambian con el vecino y en los extremos no hacen nada`() = runTest {
        val task = newTask()
        val (a, b, c) = steps.addAll(task, listOf("A", "B", "C"))

        steps.move(c, up = true)
        assertEquals(listOf("A", "C", "B"), steps.getForTask(task).map { it.text })

        steps.move(a, up = false)
        assertEquals(listOf("C", "A", "B"), steps.getForTask(task).map { it.text })

        steps.move(c, up = true)
        steps.move(b, up = false)
        assertEquals(listOf("C", "A", "B"), steps.getForTask(task).map { it.text })
    }

    @Test
    fun `quitar un paso deja los demas en su orden y anadir sigue al final`() = runTest {
        val task = newTask()
        val (a, b, _) = steps.addAll(task, listOf("A", "B", "C"))

        steps.delete(b)
        steps.addAll(task, listOf("D"))
        steps.move(a, up = false)

        assertEquals(listOf("C", "A", "D"), steps.getForTask(task).map { it.text })
    }

    @Test
    fun `borrar la tarea se lleva sus pasos`() = runTest {
        val keep = newTask("Se queda")
        val gone = newTask("Se va")
        steps.addAll(keep, listOf("k"))
        steps.addAll(gone, listOf("g1", "g2"))

        tasks.delete(tasks.getById(gone)!!)

        assertTrue(steps.getForTask(gone).isEmpty())
        assertEquals(listOf("k"), steps.observeAll().first().map { it.text })
    }

    @Test
    fun `las plantillas guardan sus pasos en orden y salen por nombre`() = runTest {
        templates.save("Hacer la compra", listOf("Lista", "Ir al super", "Guardar todo"))
        templates.save("Escribir informe", listOf("Esquema"))

        val all = templates.observeAll().first()

        assertEquals(listOf("Escribir informe", "Hacer la compra"), all.map { it.name })
        assertEquals(listOf("Lista", "Ir al super", "Guardar todo"), all.last().steps)
        assertFalse(all.any { it.builtIn })
    }

    @Test
    fun `guardar con el mismo nombre sustituye la plantilla aunque cambien las mayusculas`() = runTest {
        templates.save("Informe", listOf("Uno"))

        templates.save("INFORME", listOf("Uno", "Dos"))

        val all = templates.observeAll().first()
        assertEquals(1, all.size)
        assertEquals("INFORME", all.single().name)
        assertEquals(listOf("Uno", "Dos"), all.single().steps)
    }

    @Test
    fun `borrar una plantilla solo quita esa`() = runTest {
        templates.save("A", listOf("a"))
        templates.save("B", listOf("b"))
        val a = templates.observeAll().first().first { it.name == "A" }

        templates.delete(a.id)

        assertEquals(listOf("B"), templates.observeAll().first().map { it.name })
        assertNull(templates.observeAll().first().firstOrNull { it.name == "A" })
    }
}
