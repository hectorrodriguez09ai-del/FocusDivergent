package com.focusdivergent.app.presentation.mascot

import com.focusdivergent.app.domain.reward.Cosmetic
import com.focusdivergent.app.domain.reward.Mascot
import com.focusdivergent.app.domain.reward.RewardCause
import com.focusdivergent.app.domain.reward.RewardEvent
import com.focusdivergent.app.domain.usecase.BuyCosmeticUseCase
import com.focusdivergent.app.domain.usecase.EquipCosmeticUseCase
import com.focusdivergent.app.domain.usecase.FakeCosmetics
import com.focusdivergent.app.domain.usecase.FakeMascotSettings
import com.focusdivergent.app.domain.usecase.FakeRewards
import com.focusdivergent.app.domain.usecase.FakeRoutineCompletions
import com.focusdivergent.app.domain.usecase.FakeUserStats
import com.focusdivergent.app.domain.usecase.ObserveProgressUseCase
import com.focusdivergent.app.domain.usecase.TEST_CLOCK
import com.focusdivergent.app.domain.usecase.TEST_TODAY
import com.focusdivergent.app.presentation.MainDispatcherRule
import kotlinx.coroutines.flow.collect
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

class MascotViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val rewards = FakeRewards()
    private val cosmetics = FakeCosmetics()
    private val settings = FakeMascotSettings()

    private fun viewModel() = MascotViewModel(
        observeProgress = ObserveProgressUseCase(rewards, FakeRoutineCompletions(), cosmetics, FakeUserStats(), settings),
        settings = settings,
        buyCosmetic = BuyCosmeticUseCase(rewards, cosmetics, settings),
        equipCosmetic = EquipCosmeticUseCase(cosmetics, settings),
        clock = TEST_CLOCK,
    )

    private fun TestScope.observe(vm: MascotViewModel) {
        backgroundScope.launch(mainDispatcher.dispatcher) { vm.uiState.collect() }
    }

    private suspend fun earn(coins: Int) =
        rewards.record(RewardEvent(RewardCause.TASK_DONE, 1, TEST_TODAY, xp = 0, coins = coins))

    private suspend fun MascotViewModel.nextEvent(): MascotEvent? = withTimeoutOrNull(100) { events.first() }

    @Test
    fun `arranca sin progreso hasta que llegan los datos y luego los enseña`() = runTest {
        val vm = viewModel()
        assertNull(vm.uiState.value.progress)

        observe(vm)

        assertEquals(1, vm.uiState.value.progress?.level?.level)
        assertEquals(Mascot.FOX, vm.uiState.value.progress?.selection?.mascot)
    }

    @Test
    fun `elegir otra mascota se guarda`() = runTest {
        val vm = viewModel().also { observe(it) }

        vm.chooseMascot(Mascot.ALIEN)

        assertEquals(Mascot.ALIEN, vm.uiState.value.progress?.selection?.mascot)
        assertEquals(Mascot.ALIEN, settings.selection.value.mascot)
    }

    @Test
    fun `comprar con monedas lo pone y avisa de que se compro`() = runTest {
        earn(10)
        val vm = viewModel().also { observe(it) }

        vm.onCosmetic(Cosmetic.PROP_PLANT)

        assertEquals(MascotEvent.Bought(Cosmetic.PROP_PLANT), vm.nextEvent())
        assertEquals(Cosmetic.PROP_PLANT, vm.uiState.value.progress?.selection?.prop)
        assertEquals(5, vm.uiState.value.progress?.coins)
    }

    @Test
    fun `sin monedas suficientes avisa con calma y no cobra`() = runTest {
        earn(2)
        val vm = viewModel().also { observe(it) }

        vm.onCosmetic(Cosmetic.PROP_LAMP)

        assertEquals(MascotEvent.NotEnoughCoins(Cosmetic.PROP_LAMP), vm.nextEvent())
        assertTrue(cosmetics.purchases.isEmpty())
        assertEquals(2, vm.uiState.value.progress?.coins)
    }

    @Test
    fun `tocar algo que ya se tiene lo pone o lo quita sin avisar de compra`() = runTest {
        earn(10)
        val vm = viewModel().also { observe(it) }
        vm.onCosmetic(Cosmetic.PROP_PLANT)
        vm.nextEvent()

        vm.onCosmetic(Cosmetic.PROP_PLANT)

        assertNull(vm.uiState.value.progress?.selection?.prop)
        assertNull(vm.nextEvent())
        assertEquals(5, vm.uiState.value.progress?.coins)
    }

    @Test
    fun `volver al fondo gratuito no cuesta nada`() = runTest {
        earn(10)
        val vm = viewModel().also { observe(it) }
        vm.onCosmetic(Cosmetic.SCENERY_NIGHT)
        vm.nextEvent()

        vm.onCosmetic(Cosmetic.SCENERY_CALM)

        assertEquals(Cosmetic.SCENERY_CALM, vm.uiState.value.progress?.selection?.scenery)
        assertEquals(4, vm.uiState.value.progress?.coins)
    }
}
