package com.focusdivergent.app.presentation.today

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.focusdivergent.app.domain.reward.Cosmetic
import com.focusdivergent.app.domain.reward.Levels
import com.focusdivergent.app.domain.reward.MascotSelection
import com.focusdivergent.app.domain.reward.StreakInfo
import com.focusdivergent.app.domain.tasks.AgendaItem
import com.focusdivergent.app.domain.usecase.FakeAmbientAudio
import com.focusdivergent.app.domain.usecase.FakeCosmetics
import com.focusdivergent.app.domain.usecase.FakeDailyEnergyStore
import com.focusdivergent.app.domain.usecase.FakeMascotSettings
import com.focusdivergent.app.domain.usecase.FakeRewards
import com.focusdivergent.app.domain.usecase.FakeRoutineCompletions
import com.focusdivergent.app.domain.usecase.FakeTaskSteps
import com.focusdivergent.app.domain.usecase.FakeTasks
import com.focusdivergent.app.domain.usecase.FakeTimerStore
import com.focusdivergent.app.domain.usecase.FakeUserStats
import com.focusdivergent.app.domain.usecase.ObserveProgressUseCase
import com.focusdivergent.app.domain.usecase.Progress
import com.focusdivergent.app.domain.usecase.SetTaskDoneUseCase
import com.focusdivergent.app.domain.usecase.TEST_CLOCK
import com.focusdivergent.app.domain.usecase.TEST_TODAY
import com.focusdivergent.app.domain.usecase.task
import com.focusdivergent.app.domain.usecase.testLedger
import com.focusdivergent.app.presentation.MainDispatcherRule
import com.focusdivergent.app.presentation.theme.FocusDivergentTheme
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** La mascota en «Hoy»: que el estado la lleve y que la pantalla la enseñe y la abra. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "es-rES-w411dp-h2000dp")
class TodayMascotTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val tasks = FakeTasks()
    private val completions = FakeRoutineCompletions()
    private val rewards = FakeRewards()

    private fun viewModel() = TodayViewModel(
        tasks,
        completions,
        FakeTaskSteps(),
        FakeDailyEnergyStore(),
        SetTaskDoneUseCase(tasks, completions, TEST_CLOCK, testLedger(completions, rewards)),
        FakeTimerStore(),
        FakeAmbientAudio(),
        ObserveProgressUseCase(rewards, completions, FakeCosmetics(), FakeUserStats(), FakeMascotSettings()),
        TEST_CLOCK,
    )

    private fun TestScope.observe(vm: TodayViewModel) {
        backgroundScope.launch(mainDispatcher.dispatcher) { vm.uiState.collect() }
    }

    @Test
    fun `Hoy lleva el progreso de la mascota desde el primer momento`() = runTest {
        val vm = viewModel().also { observe(it) }

        val progress = vm.uiState.value.progress
        assertNotNull(progress)
        assertEquals(1, progress?.level?.level)
        assertEquals(1, progress?.stage)
    }

    @Test
    fun `terminar una tarea desde Hoy sube el XP del dia en la tarjeta`() = runTest {
        val item = task(1, title = "Informe").also { tasks.save(it) }
        val vm = viewModel().also { observe(it) }
        assertEquals(0, vm.uiState.value.progress?.xpToday)

        vm.setDone(
            AgendaItem(item, isDone = false, date = TEST_TODAY, time = null),
            done = true,
        )

        assertEquals(10, vm.uiState.value.progress?.xpToday)
    }

    @Test
    fun `desmarcarla devuelve el XP del dia a cero`() = runTest {
        val item = task(1, title = "Informe").also { tasks.save(it) }
        val vm = viewModel().also { observe(it) }
        val agendaItem = AgendaItem(item, isDone = false, date = TEST_TODAY, time = null)
        vm.setDone(agendaItem, done = true)

        vm.setDone(agendaItem, done = false)

        assertEquals(0, vm.uiState.value.progress?.xpToday)
        assertEquals(0, vm.uiState.value.progress?.level?.totalXp)
    }

    @Test
    fun `sin datos de progreso todavia no hay estado de mascota`() {
        assertNull(TodayUiState(today = TEST_TODAY).progress)
    }

    private val progress = Progress(
        level = Levels.of(170),
        stage = 2,
        xpToday = 35,
        coins = 3,
        streak = StreakInfo(3, 12),
        owned = setOf(Cosmetic.SCENERY_CALM),
        selection = MascotSelection(),
    )

    private fun today(state: TodayUiState, onMascot: () -> Unit = {}) = compose.setContent {
        FocusDivergentTheme {
            TodayContent(
                uiState = state,
                onChooseForMe = {},
                onLaunch = {},
                onEnergy = {},
                onEditTask = {},
                onNewTask = {},
                onSeeAllTasks = {},
                onTimer = {},
                onAudio = {},
                onSetDone = { _, _ -> },
                onLeaveForLater = {},
                onMascot = onMascot,
            )
        }
    }

    @Test
    fun `la pantalla enseña la tarjeta de la mascota, y tocarla abre Mi mascota`() {
        var opened = 0
        today(TodayUiState(today = TEST_TODAY, loading = false, progress = progress), onMascot = { opened++ })

        compose.onNodeWithText("Nivel 4").assertExists()
        compose.onNodeWithContentDescription("Zorro, Alegre. Nivel 4.").performClick()

        assertEquals(1, opened)
    }

    @Test
    fun `sin progreso cargado no hay tarjeta`() {
        today(TodayUiState(today = TEST_TODAY, loading = false, progress = null))

        compose.onNodeWithText("Nivel 4").assertDoesNotExist()
    }
}
