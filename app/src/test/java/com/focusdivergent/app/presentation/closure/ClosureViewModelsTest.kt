package com.focusdivergent.app.presentation.closure

import androidx.lifecycle.SavedStateHandle
import com.focusdivergent.app.domain.closure.ClosureRecord
import com.focusdivergent.app.domain.closure.DayClosureConfig
import com.focusdivergent.app.domain.closure.DayClosureState
import com.focusdivergent.app.domain.model.AppLimit
import com.focusdivergent.app.domain.model.BrainDump
import com.focusdivergent.app.domain.model.TaskStatus
import com.focusdivergent.app.domain.usecase.ChangeClosureSettingsUseCase
import com.focusdivergent.app.domain.usecase.FakeAppLimits
import com.focusdivergent.app.domain.usecase.FakeBrainDump
import com.focusdivergent.app.domain.usecase.FakeClosureAlarms
import com.focusdivergent.app.domain.usecase.FakeDayClosureSettings
import com.focusdivergent.app.domain.usecase.FakeDayClosureStore
import com.focusdivergent.app.domain.usecase.FakeRewards
import com.focusdivergent.app.domain.usecase.FakeRoutineCompletions
import com.focusdivergent.app.domain.usecase.FakeTasks
import com.focusdivergent.app.domain.usecase.MutableClock
import com.focusdivergent.app.domain.usecase.ObserveDaySummaryUseCase
import com.focusdivergent.app.domain.usecase.ScheduleDayClosureUseCase
import com.focusdivergent.app.domain.usecase.TEST_NOW
import com.focusdivergent.app.domain.usecase.TEST_TODAY
import com.focusdivergent.app.domain.usecase.task
import com.focusdivergent.app.presentation.MainDispatcherRule
import java.time.Instant
import java.time.LocalTime
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ClosureViewModelsTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val clock = MutableClock(Instant.parse("2026-10-10T12:00:00Z"))
    private val settings = FakeDayClosureSettings()
    private val limits = FakeAppLimits()
    private val store = FakeDayClosureStore()
    private val tasks = FakeTasks()
    private val rewards = FakeRewards()

    private fun <T> TestScope.observe(flow: kotlinx.coroutines.flow.StateFlow<T>) {
        backgroundScope.launch(mainDispatcher.dispatcher) { flow.collect() }
    }

    // ---- Resumen

    private fun summaryVm(day: Long? = null) = ClosureSummaryViewModel(
        savedStateHandle = SavedStateHandle(if (day == null) emptyMap() else mapOf(ClosureSummaryViewModel.ARG_DAY to day)),
        observeSummary = ObserveDaySummaryUseCase(tasks, FakeRoutineCompletions(), rewards, FakeBrainDump(listOf(BrainDump(1, "idea", TEST_NOW))), store, clock),
        limits = limits,
        settings = settings,
        clock = clock,
    )

    @Test
    fun `el resumen sin dia explicito es el de hoy`() = runTest {
        val vm = summaryVm().also { observe(it.uiState) }

        assertEquals(TEST_TODAY, vm.uiState.value.summary?.day)
        assertEquals(1, vm.uiState.value.summary?.ideasWaiting)
    }

    @Test
    fun `el dia 0 tambien es hoy`() = runTest {
        val vm = summaryVm(day = 0).also { observe(it.uiState) }

        assertEquals(TEST_TODAY, vm.uiState.value.summary?.day)
    }

    @Test
    fun `con un dia explicito se enseña el resumen de ese dia`() = runTest {
        val yesterday = TEST_TODAY.minusDays(1)
        tasks.save(task(1, title = "Informe", status = TaskStatus.DONE, completedAt = Instant.parse("2026-10-09T10:00:00Z")))

        val vm = summaryVm(day = yesterday.toEpochDay()).also { observe(it.uiState) }

        assertEquals(yesterday, vm.uiState.value.summary?.day)
        assertEquals(listOf("Informe"), vm.uiState.value.summary?.doneTitles)
    }

    @Test
    fun `el resumen sabe cuantas apps de ocio hay y hasta cuando quedan libres`() = runTest {
        limits.save(AppLimit("com.example.a", 30, isLeisure = true))
        limits.save(AppLimit("com.example.b", 30, isLeisure = true))
        limits.save(AppLimit("com.example.c", 30))
        val vm = summaryVm().also { observe(it.uiState) }

        assertEquals(2, vm.uiState.value.leisureApps)
        assertEquals(LocalTime.of(6, 0), vm.uiState.value.liftUntil)
    }

    @Test
    fun `con el cierre desactivado no hay hora hasta la que queden libres`() = runTest {
        settings.update(DayClosureState(effective = DayClosureConfig(enabled = false)))
        val vm = summaryVm().also { observe(it.uiState) }

        assertNull(vm.uiState.value.liftUntil)
    }

    @Test
    fun `el resumen lleva lo trasladado cuando se cerro ese dia`() = runTest {
        store.save(ClosureRecord(TEST_TODAY, moved = 4, respectedLimits = 1))
        val vm = summaryVm().also { observe(it.uiState) }

        assertEquals(4, vm.uiState.value.summary?.moved)
        assertEquals(1, vm.uiState.value.summary?.respectedLimits)
    }

    // ---- Ajustes

    private val alarms = FakeClosureAlarms()

    private fun settingsVm() = ClosureSettingsViewModel(
        settings = settings,
        limits = limits,
        change = ChangeClosureSettingsUseCase(settings, ScheduleDayClosureUseCase(settings, alarms, clock), clock),
        clock = clock,
    )

    @Test
    fun `los ajustes arrancan con los de serie y sin nada pendiente`() = runTest {
        val vm = settingsVm().also { observe(it.uiState) }

        assertEquals(DayClosureConfig(), vm.uiState.value.effective)
        assertNull(vm.uiState.value.pending)
        assertEquals(DayClosureConfig(), vm.uiState.value.chosen)
    }

    @Test
    fun `desactivar el cierre rige al momento`() = runTest {
        val vm = settingsVm().also { observe(it.uiState) }

        vm.setEnabled(false)

        assertFalse(vm.uiState.value.effective.enabled)
        assertNull(vm.uiState.value.pending)
    }

    @Test
    fun `activarlo de nuevo espera a medianoche, y lo elegido ya se ve activado`() = runTest {
        val vm = settingsVm().also { observe(it.uiState) }
        vm.setEnabled(false)

        vm.setEnabled(true)

        assertFalse(vm.uiState.value.effective.enabled)
        assertNotNull(vm.uiState.value.pending)
        assertTrue(vm.uiState.value.chosen.enabled)
        assertEquals(Instant.parse("2026-10-11T00:00:00Z"), vm.uiState.value.pending?.appliesAt)
    }

    @Test
    fun `cerrar mas tarde rige ya y cerrar antes queda pendiente`() = runTest {
        val vm = settingsVm().also { observe(it.uiState) }

        vm.setCloseAt(LocalTime.of(23, 0))
        assertEquals(LocalTime.of(23, 0), vm.uiState.value.effective.closeAt)
        assertNull(vm.uiState.value.pending)

        vm.setCloseAt(LocalTime.of(20, 0))
        assertEquals(LocalTime.of(23, 0), vm.uiState.value.effective.closeAt)
        assertEquals(LocalTime.of(20, 0), vm.uiState.value.pending?.config?.closeAt)
    }

    @Test
    fun `alargar la noche queda pendiente y acortarla rige ya`() = runTest {
        val vm = settingsVm().also { observe(it.uiState) }

        vm.setLiftUntil(LocalTime.of(8, 0))
        assertEquals(LocalTime.of(6, 0), vm.uiState.value.effective.liftUntil)
        assertEquals(LocalTime.of(8, 0), vm.uiState.value.chosen.liftUntil)

        vm.setLiftUntil(LocalTime.of(5, 0))
        assertEquals(LocalTime.of(5, 0), vm.uiState.value.effective.liftUntil)
        assertNull(vm.uiState.value.pending)
    }

    @Test
    fun `cambiar una hora no pisa lo que ya se habia pedido en la otra`() = runTest {
        val vm = settingsVm().also { observe(it.uiState) }
        vm.setLiftUntil(LocalTime.of(8, 0)) // pendiente

        vm.setCloseAt(LocalTime.of(23, 0)) // endurece: rige ya

        assertEquals(LocalTime.of(23, 0), vm.uiState.value.effective.closeAt)
        assertEquals(LocalTime.of(8, 0), vm.uiState.value.pending?.config?.liftUntil)
        assertEquals(LocalTime.of(23, 0), vm.uiState.value.pending?.config?.closeAt)
    }

    @Test
    fun `cancelar el cambio deja lo que rige`() = runTest {
        val vm = settingsVm().also { observe(it.uiState) }
        vm.setLiftUntil(LocalTime.of(8, 0))

        vm.cancelPending()

        assertNull(vm.uiState.value.pending)
        assertEquals(LocalTime.of(6, 0), vm.uiState.value.chosen.liftUntil)
    }

    @Test
    fun `un cambio cuya hora ya llego ya no se enseña como pendiente`() = runTest {
        val vm = settingsVm().also { observe(it.uiState) }
        vm.setLiftUntil(LocalTime.of(8, 0))

        clock.now = Instant.parse("2026-10-11T00:00:01Z")
        // Hace falta una emisión nueva para releer la hora: un cambio de las apps la provoca.
        limits.save(AppLimit("com.example.a", 30))

        assertNull(vm.uiState.value.pending)
        assertEquals(LocalTime.of(8, 0), vm.uiState.value.effective.liftUntil)
    }

    @Test
    fun `los ajustes cuentan las apps de ocio`() = runTest {
        limits.save(AppLimit("com.example.a", 30, isLeisure = true))
        limits.save(AppLimit("com.example.b", 30))
        val vm = settingsVm().also { observe(it.uiState) }

        assertEquals(1, vm.uiState.value.leisureApps)
        assertEquals(TEST_TODAY.toEpochDay(), vm.today())
    }

    @Test
    fun `cada cambio deja la alarma al dia`() = runTest {
        val vm = settingsVm().also { observe(it.uiState) }

        vm.setCloseAt(LocalTime.of(23, 0))

        assertEquals(Instant.parse("2026-10-10T23:00:00Z"), alarms.scheduledAt)
    }
}
