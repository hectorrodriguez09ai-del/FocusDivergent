package com.focusdivergent.app.data.repository

import android.app.Application
import com.focusdivergent.app.data.local.FocusDatabase
import com.focusdivergent.app.domain.model.EnergyLevel
import com.focusdivergent.app.domain.model.Task
import com.focusdivergent.app.domain.model.TaskStatus
import com.focusdivergent.app.domain.model.TaskType
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TaskRepositoryImplTest {

    private lateinit var db: FocusDatabase
    private lateinit var repository: TaskRepositoryImpl

    @Before
    fun setUp() {
        db = createTestDatabase()
        repository = TaskRepositoryImpl(db.taskDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `save devuelve el id nuevo al insertar y el mismo id al actualizar`() = runTest {
        val newId = repository.save(Task(title = "Regar plantas", type = TaskType.TASK, energyLevel = EnergyLevel.LOW))

        val saved = repository.getById(newId)!!
        val updatedId = repository.save(saved.copy(title = "Regar las plantas del balcón"))

        assertEquals(saved.id, newId)
        assertEquals(newId, updatedId)
        assertEquals("Regar las plantas del balcón", repository.getById(newId)?.title)
        assertEquals(1, repository.observeAll().first().size)
    }

    @Test
    fun `observeByTypeBetween devuelve solo las obligaciones del rango con sus instantes`() = runTest {
        val inRange = Instant.parse("2026-10-09T10:00:00Z")
        repository.save(
            Task(title = "Cita médica", type = TaskType.OBLIGATION, energyLevel = EnergyLevel.MEDIUM, startTime = inRange),
        )
        repository.save(
            Task(
                title = "Entrega",
                type = TaskType.OBLIGATION,
                energyLevel = EnergyLevel.HIGH,
                startTime = Instant.parse("2026-10-12T10:00:00Z"),
            ),
        )
        repository.save(
            Task(title = "Paseo", type = TaskType.TASK, energyLevel = EnergyLevel.LOW, startTime = inRange),
        )

        val result = repository.observeByTypeBetween(
            TaskType.OBLIGATION,
            from = Instant.parse("2026-10-09T00:00:00Z"),
            to = Instant.parse("2026-10-09T23:59:59Z"),
        ).first()

        assertEquals(listOf("Cita médica"), result.map { it.title })
        assertEquals(inRange, result.single().startTime)
    }

    private val now = Instant.parse("2026-10-10T12:00:00Z")

    private suspend fun saveTask(
        title: String,
        startsInMinutes: Long?,
        type: TaskType = TaskType.OBLIGATION,
        status: TaskStatus = TaskStatus.PENDING,
    ) = repository.save(
        Task(
            title = title,
            type = type,
            energyLevel = EnergyLevel.MEDIUM,
            status = status,
            startTime = startsInMinutes?.let { now.plusSeconds(it * 60) },
        ),
    )

    @Test
    fun `las obligaciones pendientes por empezar se filtran por tipo, estado y rango, y salen en orden`() = runTest {
        saveTask("Tarde", 8)
        saveTask("Pronto", 3)
        saveTask("Hecha", 4, status = TaskStatus.DONE)
        saveTask("En marcha", 4, status = TaskStatus.IN_PROGRESS)
        saveTask("Es una tarea", 4, type = TaskType.TASK)
        saveTask("Sin hora", null)
        saveTask("Demasiado lejos", 25)
        saveTask("Ya empezó", -5)

        val result = repository.getPendingObligationsStartingBetween(now, now.plusSeconds(10 * 60))

        assertEquals(listOf("Pronto", "Tarde"), result.map { it.title })
    }

    @Test
    fun `el rango excluye el inicio exacto de from e incluye el de to`() = runTest {
        saveTask("Empieza ahora", 0)
        saveTask("Justo al limite", 10)

        val result = repository.getPendingObligationsStartingBetween(now, now.plusSeconds(10 * 60))

        assertEquals(listOf("Justo al limite"), result.map { it.title })
    }

    @Test
    fun `la siguiente obligacion pendiente es la primera que empieza despues del instante dado`() = runTest {
        saveTask("Lejana", 120)
        saveTask("Cercana", 30)
        saveTask("Hecha", 20, status = TaskStatus.DONE)
        saveTask("Pasada", -10)
        saveTask("Es una rutina", 15, type = TaskType.ROUTINE)

        assertEquals("Cercana", repository.getNextPendingObligationStartingAfter(now)?.title)
        assertEquals("Lejana", repository.getNextPendingObligationStartingAfter(now.plusSeconds(30 * 60))?.title)
        assertNull(repository.getNextPendingObligationStartingAfter(now.plusSeconds(120 * 60)))
    }

    @Test
    fun `observeByEnergy filtra por nivel`() = runTest {
        repository.save(Task(title = "Fregar", type = TaskType.TASK, energyLevel = EnergyLevel.LOW))
        repository.save(Task(title = "Declaración", type = TaskType.TASK, energyLevel = EnergyLevel.HIGH))

        assertEquals(listOf("Fregar"), repository.observeByEnergy(EnergyLevel.LOW).first().map { it.title })
    }

    @Test
    fun `updateStatus y delete actualizan lo guardado`() = runTest {
        val id = repository.save(Task(title = "Llamar al banco", type = TaskType.TASK, energyLevel = EnergyLevel.MEDIUM))

        repository.updateStatus(id, TaskStatus.DONE)
        assertEquals(TaskStatus.DONE, repository.getById(id)?.status)

        repository.delete(repository.getById(id)!!)
        assertNull(repository.getById(id))
    }
}
