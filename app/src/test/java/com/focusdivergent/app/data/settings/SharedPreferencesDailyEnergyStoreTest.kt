package com.focusdivergent.app.data.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.focusdivergent.app.domain.model.EnergyLevel
import com.focusdivergent.app.domain.model.TaskStatus
import com.focusdivergent.app.domain.model.TaskType
import com.focusdivergent.app.domain.usecase.FakeTasks
import com.focusdivergent.app.domain.usecase.StartTaskUseCase
import com.focusdivergent.app.domain.usecase.TEST_TODAY
import com.focusdivergent.app.domain.usecase.task
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
class SharedPreferencesDailyEnergyStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clean() {
        context.getSharedPreferences("daily_energy", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `al principio no hay energia elegida`() = runTest {
        val store = SharedPreferencesDailyEnergyStore(context)

        assertNull(store.get(TEST_TODAY))
        assertNull(store.observe(TEST_TODAY).first())
    }

    @Test
    fun `la energia elegida se recuerda ese dia y se observa`() = runTest {
        val store = SharedPreferencesDailyEnergyStore(context)

        store.set(TEST_TODAY, EnergyLevel.HIGH)

        assertEquals(EnergyLevel.HIGH, store.get(TEST_TODAY))
        assertEquals(EnergyLevel.HIGH, store.observe(TEST_TODAY).first())
    }

    @Test
    fun `al dia siguiente la energia de ayer ya no cuenta`() = runTest {
        val store = SharedPreferencesDailyEnergyStore(context)

        store.set(TEST_TODAY, EnergyLevel.LOW)

        assertNull(store.get(TEST_TODAY.plusDays(1)))
        assertNull(store.observe(TEST_TODAY.plusDays(1)).first())
        assertNull(store.get(TEST_TODAY.minusDays(1)))
    }

    @Test
    fun `elegir de nuevo sustituye a la anterior`() = runTest {
        val store = SharedPreferencesDailyEnergyStore(context)

        store.set(TEST_TODAY, EnergyLevel.LOW)
        store.set(TEST_TODAY, EnergyLevel.MEDIUM)

        assertEquals(EnergyLevel.MEDIUM, store.get(TEST_TODAY))
    }

    @Test
    fun `sobrevive a que la app se cierre y se vuelva a abrir`() = runTest {
        SharedPreferencesDailyEnergyStore(context).set(TEST_TODAY, EnergyLevel.MEDIUM)

        val reopened = SharedPreferencesDailyEnergyStore(context)

        assertEquals(EnergyLevel.MEDIUM, reopened.get(TEST_TODAY))
        assertNull(reopened.get(TEST_TODAY.plusDays(1)))
    }

    @Test
    fun `un valor guardado que no se entiende se ignora en vez de fallar`() = runTest {
        context.getSharedPreferences("daily_energy", Context.MODE_PRIVATE).edit()
            .putLong("day", TEST_TODAY.toEpochDay()).putInt("level", 99).commit()

        assertNull(SharedPreferencesDailyEnergyStore(context).get(TEST_TODAY))
    }

    // ---- Empezar una tarea

    @Test
    fun `empezar una tarea la deja en marcha`() = runTest {
        val tasks = FakeTasks(listOf(task(1)))

        assertTrue(StartTaskUseCase(tasks)(tasks.items.single()))

        assertEquals(TaskStatus.IN_PROGRESS, tasks.items.single().status)
        assertNull(tasks.items.single().completedAt)
    }

    @Test
    fun `empezar una rutina, una obligacion o algo ya hecho no cambia nada`() = runTest {
        val routine = task(1, type = TaskType.ROUTINE)
        val obligation = task(2, type = TaskType.OBLIGATION, startsInMinutes = 60)
        val done = task(3, status = TaskStatus.DONE)
        val tasks = FakeTasks(listOf(routine, obligation, done))
        val start = StartTaskUseCase(tasks)

        assertFalse(start(routine))
        assertFalse(start(obligation))
        assertFalse(start(done))

        assertEquals(listOf(TaskStatus.PENDING, TaskStatus.PENDING, TaskStatus.DONE), tasks.items.map { it.status })
    }
}
