package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.reward.Cosmetic
import com.focusdivergent.app.domain.reward.CosmeticPurchase
import com.focusdivergent.app.domain.reward.Mascot
import com.focusdivergent.app.domain.reward.MascotSelection
import com.focusdivergent.app.domain.reward.RewardCause
import com.focusdivergent.app.domain.reward.RewardEvent
import com.focusdivergent.app.domain.reward.RewardRules
import java.time.LocalDate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MascotUseCasesTest {

    private val completions = FakeRoutineCompletions()
    private val rewards = FakeRewards()
    private val cosmetics = FakeCosmetics()
    private val stats = FakeUserStats()
    private val settings = FakeMascotSettings()

    private val observe = ObserveProgressUseCase(rewards, completions, cosmetics, stats, settings)
    private val buy = BuyCosmeticUseCase(rewards, cosmetics, settings)
    private val equip = EquipCosmeticUseCase(cosmetics, settings)

    private var nextRef = 1L

    /** Le da al usuario [coins] monedas, repartidas en días distintos para no chocar con el tope diario. */
    private suspend fun earn(coins: Int) {
        var left = coins
        var day = LocalDate.of(2026, 9, 1)
        while (left > 0) {
            val today = minOf(left, RewardRules.DAILY_COIN_CAP)
            rewards.record(RewardEvent(RewardCause.TASK_DONE, nextRef++, day, xp = 0, coins = today))
            left -= today
            day = day.plusDays(1)
        }
    }

    private suspend fun progress() = observe(TEST_TODAY).first()

    // ---- Progreso

    @Test
    fun `sin nada hecho el usuario esta en el nivel 1, etapa 1, sin monedas y con el fondo gratuito`() = runTest {
        val p = progress()

        assertEquals(1, p.level.level)
        assertEquals(1, p.stage)
        assertEquals(0, p.xpToday)
        assertEquals(0, p.coins)
        assertEquals(0, p.streak.current)
        assertEquals(setOf(Cosmetic.SCENERY_CALM), p.owned)
        assertEquals(MascotSelection(), p.selection)
    }

    @Test
    fun `el nivel sale del XP con el tope diario y la etapa del nivel`() = runTest {
        // Tres días de 250 XP brutos: el tope los deja en 100 cada uno, 300 en total, que es el nivel 6 (etapa 3).
        for (d in 8..10) rewards.record(RewardEvent(RewardCause.TASK_DONE, d.toLong(), LocalDate.of(2026, 10, d), xp = 250, coins = 0))

        val p = progress()

        assertEquals(6, p.level.level)
        assertEquals(3, p.stage)
        assertEquals(RewardRules.DAILY_XP_CAP, p.xpToday)
    }

    @Test
    fun `la racha de Rutinas sale de los dias marcados y la mejor guardada no baja`() = runTest {
        stats.raiseBestStreak(9)
        for (d in 9..10) completions.markCompleted(1, LocalDate.of(2026, 10, d), TEST_NOW)

        val p = progress()

        assertEquals(2, p.streak.current)
        assertEquals(9, p.streak.best)
    }

    @Test
    fun `una seleccion que menciona algo que no se posee vuelve a lo gratuito`() = runTest {
        settings.update { MascotSelection(Mascot.CAT, Cosmetic.SCENERY_NEBULA, Cosmetic.PROP_LAMP) }

        val p = progress()

        assertEquals(MascotSelection(Mascot.CAT, Cosmetic.SCENERY_CALM, null), p.selection)
    }

    // ---- Comprar

    @Test
    fun `comprar con monedas de sobra descuenta el precio, lo da y lo pone`() = runTest {
        earn(10)

        val result = buy(Cosmetic.PROP_PLANT)

        assertEquals(PurchaseResult.BOUGHT, result)
        val p = progress()
        assertEquals(10 - Cosmetic.PROP_PLANT.price, p.coins)
        assertTrue(Cosmetic.PROP_PLANT in p.owned)
        assertEquals(Cosmetic.PROP_PLANT, p.selection.prop)
    }

    @Test
    fun `sin monedas suficientes no se compra nada y no se descuenta`() = runTest {
        earn(Cosmetic.PROP_LAMP.price - 1)

        val result = buy(Cosmetic.PROP_LAMP)

        assertEquals(PurchaseResult.NOT_ENOUGH_COINS, result)
        assertTrue(cosmetics.purchases.isEmpty())
        assertEquals(Cosmetic.PROP_LAMP.price - 1, progress().coins)
        assertNull(progress().selection.prop)
    }

    @Test
    fun `con exactamente las monedas justas se puede comprar`() = runTest {
        earn(Cosmetic.SCENERY_NIGHT.price)

        assertEquals(PurchaseResult.BOUGHT, buy(Cosmetic.SCENERY_NIGHT))
        assertEquals(0, progress().coins)
    }

    @Test
    fun `lo que ya se tiene no se cobra dos veces`() = runTest {
        earn(30)
        buy(Cosmetic.PROP_BOOKS)

        assertEquals(PurchaseResult.ALREADY_OWNED, buy(Cosmetic.PROP_BOOKS))

        assertEquals(30 - Cosmetic.PROP_BOOKS.price, progress().coins)
        assertEquals(1, cosmetics.purchases.size)
    }

    @Test
    fun `lo gratuito no se compra`() = runTest {
        assertEquals(PurchaseResult.ALREADY_OWNED, buy(Cosmetic.SCENERY_CALM))
        assertTrue(cosmetics.purchases.isEmpty())
    }

    @Test
    fun `dos compras a la vez no pueden gastar mas de lo que hay`() = runTest {
        // Monedas para uno solo de los dos objetos.
        earn(Cosmetic.PROP_LAMP.price)

        val results = listOf(
            async { buy(Cosmetic.PROP_LAMP) },
            async { buy(Cosmetic.PROP_STAR) },
        ).awaitAll()

        assertEquals(1, results.count { it == PurchaseResult.BOUGHT })
        assertEquals(1, cosmetics.purchases.size)
        assertTrue(progress().coins >= 0)
    }

    @Test
    fun `el saldo no baja de cero aunque deshacer una tarea quite monedas ya gastadas`() = runTest {
        earn(5)
        buy(Cosmetic.PROP_PLANT)
        rewards.retract(RewardCause.TASK_DONE, 1)

        assertEquals(0, progress().coins)
    }

    @Test
    fun `las monedas tambien tienen tope diario`() = runTest {
        rewards.record(RewardEvent(RewardCause.TASK_DONE, 1, LocalDate.of(2026, 10, 10), xp = 0, coins = 50))

        assertEquals(RewardRules.DAILY_COIN_CAP, progress().coins)
    }

    // ---- Poner y quitar

    @Test
    fun `se puede poner un fondo comprado y volver al gratuito`() = runTest {
        cosmetics.add(CosmeticPurchase(Cosmetic.SCENERY_FOREST, 10))

        assertTrue(equip(Cosmetic.SCENERY_FOREST))
        assertEquals(Cosmetic.SCENERY_FOREST, progress().selection.scenery)

        assertTrue(equip(Cosmetic.SCENERY_CALM))
        assertEquals(Cosmetic.SCENERY_CALM, progress().selection.scenery)
    }

    @Test
    fun `tocar un objeto que ya se lleva lo quita`() = runTest {
        cosmetics.add(CosmeticPurchase(Cosmetic.PROP_STAR, 16))
        equip(Cosmetic.PROP_STAR)
        assertEquals(Cosmetic.PROP_STAR, progress().selection.prop)

        equip(Cosmetic.PROP_STAR)

        assertNull(progress().selection.prop)
    }

    @Test
    fun `tocar un fondo que ya se lleva lo deja puesto`() = runTest {
        equip(Cosmetic.SCENERY_CALM)
        equip(Cosmetic.SCENERY_CALM)

        assertEquals(Cosmetic.SCENERY_CALM, progress().selection.scenery)
    }

    @Test
    fun `no se puede poner algo que no se posee`() = runTest {
        assertFalse(equip(Cosmetic.PROP_LAMP))

        assertNull(settings.selection.value.prop)
    }

    @Test
    fun `cambiar de mascota no toca el XP ni las compras`() = runTest {
        earn(10)
        buy(Cosmetic.PROP_PLANT)
        val before = progress()

        settings.update { it.copy(mascot = Mascot.ALIEN) }
        val after = progress()

        assertEquals(before.level, after.level)
        assertEquals(before.coins, after.coins)
        assertEquals(before.owned, after.owned)
        assertEquals(Mascot.ALIEN, after.selection.mascot)
        assertEquals(Cosmetic.PROP_PLANT, after.selection.prop)
    }
}
