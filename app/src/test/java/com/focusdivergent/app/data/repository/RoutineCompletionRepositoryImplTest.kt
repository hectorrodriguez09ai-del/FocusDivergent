package com.focusdivergent.app.data.repository

import android.app.Application
import com.focusdivergent.app.data.local.FocusDatabase
import com.focusdivergent.app.domain.model.EnergyLevel
import com.focusdivergent.app.domain.model.Task
import com.focusdivergent.app.domain.model.TaskStatus
import com.focusdivergent.app.domain.model.TaskType
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RoutineCompletionRepositoryImplTest {

    private lateinit var db: FocusDatabase
    private lateinit var tasks: TaskRepositoryImpl
    private lateinit var completions: RoutineCompletionRepositoryImpl

    private val saturday = LocalDate.of(2026, 10, 10)
    private val now = Instant.parse("2026-10-10T12:00:00Z")

    @Before
    fun setUp() {
        db = createTestDatabase()
        tasks = TaskRepositoryImpl(db.taskDao())
        completions = RoutineCompletionRepositoryImpl(db.routineCompletionDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun routine(title: String) =
        tasks.save(Task(title = title, type = TaskType.ROUTINE, energyLevel = EnergyLevel.LOW))

    @Test
    fun `una rutina marcada un dia solo consta ese dia`() = runTest {
        val id = routine("Medicación")

        completions.markCompleted(id, saturday, now)

        assertEquals(setOf(id), completions.observeCompletedOn(saturday).first())
        assertEquals(emptySet<Long>(), completions.observeCompletedOn(saturday.plusDays(1)).first())
        assertEquals(emptySet<Long>(), completions.observeCompletedOn(saturday.minusDays(1)).first())
    }

    @Test
    fun `marcar dos veces el mismo dia no falla ni duplica`() = runTest {
        val id = routine("Medicación")

        completions.markCompleted(id, saturday, now)
        completions.markCompleted(id, saturday, now.plusSeconds(60))

        assertEquals(setOf(id), completions.observeCompletedOn(saturday).first())
    }

    @Test
    fun `desmarcar quita solo la marca de ese dia y de esa rutina`() = runTest {
        val meds = routine("Medicación")
        val walk = routine("Paseo")
        completions.markCompleted(meds, saturday, now)
        completions.markCompleted(meds, saturday.plusDays(1), now)
        completions.markCompleted(walk, saturday, now)

        completions.unmarkCompleted(meds, saturday)

        assertEquals(setOf(walk), completions.observeCompletedOn(saturday).first())
        assertEquals(setOf(meds), completions.observeCompletedOn(saturday.plusDays(1)).first())
    }

    @Test
    fun `al borrar la rutina se borran sus marcas`() = runTest {
        val id = routine("Medicación")
        completions.markCompleted(id, saturday, now)

        tasks.delete(tasks.getById(id)!!)

        assertEquals(emptySet<Long>(), completions.observeCompletedOn(saturday).first())
    }

    @Test
    fun `los campos nuevos de una actividad se guardan y se leen`() = runTest {
        val id = tasks.save(
            Task(title = "Informe", type = TaskType.TASK, energyLevel = EnergyLevel.HIGH, estimatedMinutes = 45),
        )
        assertEquals(45, tasks.getById(id)?.estimatedMinutes)
        assertEquals(null, tasks.getById(id)?.completedAt)

        tasks.updateStatus(id, TaskStatus.DONE, completedAt = now)
        assertEquals(TaskStatus.DONE, tasks.getById(id)?.status)
        assertEquals(now, tasks.getById(id)?.completedAt)

        // Volver a pendiente borra la fecha de completado.
        tasks.updateStatus(id, TaskStatus.PENDING)
        assertEquals(null, tasks.getById(id)?.completedAt)
        assertEquals(45, tasks.getById(id)?.estimatedMinutes)
    }
}
