package com.focusdivergent.app.data.repository

import android.app.Application
import com.focusdivergent.app.domain.reward.Cosmetic
import com.focusdivergent.app.domain.reward.CosmeticPurchase
import com.focusdivergent.app.domain.reward.DayReward
import com.focusdivergent.app.domain.reward.RewardCause
import com.focusdivergent.app.domain.reward.RewardEvent
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** El libro de XP y las compras sobre Room de verdad: aquí vive la garantía de «una vez por causa, actividad y día». */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RewardRepositoriesTest {

    private val monday = LocalDate.of(2026, 10, 5)
    private val tuesday = LocalDate.of(2026, 10, 6)

    private fun event(cause: RewardCause = RewardCause.TASK_DONE, ref: Long = 1, day: LocalDate = monday, xp: Int = 10, coins: Int = 1) =
        RewardEvent(cause, ref, day, xp, coins)

    @Test
    fun `sin movimientos no hay totales`() = runTest {
        val db = createTestDatabase()
        try {
            assertTrue(RewardRepositoryImpl(db.xpEventDao()).observeDailyTotals().first().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun `los totales suman por dia todos los movimientos`() = runTest {
        val db = createTestDatabase()
        try {
            val repository = RewardRepositoryImpl(db.xpEventDao())
            repository.record(event(ref = 1, xp = 10, coins = 1))
            repository.record(event(ref = 2, xp = 15, coins = 1))
            repository.record(event(cause = RewardCause.ROUTINE_DONE, ref = 1, xp = 8, coins = 1))
            repository.record(event(ref = 3, day = tuesday, xp = 10, coins = 1))

            val totals = repository.observeDailyTotals().first().sortedBy { it.day }

            assertEquals(listOf(DayReward(monday, 33, 3), DayReward(tuesday, 10, 1)), totals)
        } finally {
            db.close()
        }
    }

    @Test
    fun `repetir el mismo movimiento no suma dos veces`() = runTest {
        val db = createTestDatabase()
        try {
            val repository = RewardRepositoryImpl(db.xpEventDao())

            repeat(3) { repository.record(event()) }

            assertEquals(listOf(DayReward(monday, 10, 1)), repository.observeDailyTotals().first())
        } finally {
            db.close()
        }
    }

    @Test
    fun `los totales del dia suman tambien los minutos de enfoque`() = runTest {
        val db = createTestDatabase()
        try {
            val repository = RewardRepositoryImpl(db.xpEventDao())
            repository.record(RewardEvent(RewardCause.FOCUS_SESSION, 1, monday, xp = 15, coins = 1, minutes = 45))
            repository.record(RewardEvent(RewardCause.FOCUS_SESSION, 2, monday, xp = 5, coins = 1, minutes = 25))
            repository.record(event(ref = 9, xp = 10, coins = 1))

            assertEquals(listOf(DayReward(monday, 30, 3, 70)), repository.observeDailyTotals().first())
        } finally {
            db.close()
        }
    }

    @Test
    fun `la misma actividad otro dia o con otra causa es otro movimiento`() = runTest {
        val db = createTestDatabase()
        try {
            val repository = RewardRepositoryImpl(db.xpEventDao())

            repository.record(event(day = monday))
            repository.record(event(day = tuesday))
            repository.record(event(cause = RewardCause.ROUTINE_DONE, day = monday))

            assertEquals(30, repository.observeDailyTotals().first().sumOf { it.xp })
        } finally {
            db.close()
        }
    }

    @Test
    fun `retirar borra los movimientos de esa causa y actividad en cualquier dia`() = runTest {
        val db = createTestDatabase()
        try {
            val repository = RewardRepositoryImpl(db.xpEventDao())
            repository.record(event(ref = 1, day = monday))
            repository.record(event(ref = 1, day = tuesday))
            repository.record(event(ref = 2, day = monday))
            repository.record(event(cause = RewardCause.ROUTINE_DONE, ref = 1, day = monday))

            repository.retract(RewardCause.TASK_DONE, refId = 1)

            // Quedan la otra tarea y la rutina con el mismo id.
            assertEquals(20, repository.observeDailyTotals().first().sumOf { it.xp })
        } finally {
            db.close()
        }
    }

    @Test
    fun `retirar con un dia solo borra ese dia`() = runTest {
        val db = createTestDatabase()
        try {
            val repository = RewardRepositoryImpl(db.xpEventDao())
            repository.record(event(cause = RewardCause.ROUTINE_DONE, ref = 1, day = monday, xp = 8))
            repository.record(event(cause = RewardCause.ROUTINE_DONE, ref = 1, day = tuesday, xp = 8))

            repository.retract(RewardCause.ROUTINE_DONE, refId = 1, day = monday)

            assertEquals(listOf(DayReward(tuesday, 8, 1)), repository.observeDailyTotals().first())
        } finally {
            db.close()
        }
    }

    @Test
    fun `retirar algo que no existe no hace nada`() = runTest {
        val db = createTestDatabase()
        try {
            val repository = RewardRepositoryImpl(db.xpEventDao())
            repository.record(event())

            repository.retract(RewardCause.TASK_DONE, refId = 404)

            assertEquals(10, repository.observeDailyTotals().first().sumOf { it.xp })
        } finally {
            db.close()
        }
    }

    // ---- Cosméticos

    @Test
    fun `las compras se guardan con su precio y comprar dos veces no duplica`() = runTest {
        val db = createTestDatabase()
        try {
            val repository = CosmeticRepositoryImpl(db.cosmeticPurchaseDao())
            assertTrue(repository.observePurchases().first().isEmpty())

            repository.add(CosmeticPurchase(Cosmetic.PROP_LAMP, 12))
            repository.add(CosmeticPurchase(Cosmetic.PROP_LAMP, 99))
            repository.add(CosmeticPurchase(Cosmetic.SCENERY_DUSK, 14))

            val purchases = repository.observePurchases().first().sortedBy { it.cosmetic.name }
            assertEquals(
                listOf(CosmeticPurchase(Cosmetic.PROP_LAMP, 12), CosmeticPurchase(Cosmetic.SCENERY_DUSK, 14)),
                purchases,
            )
        } finally {
            db.close()
        }
    }

    @Test
    fun `un cosmetico que esta version no conoce se ignora en vez de romper la lista`() = runTest {
        val db = createTestDatabase()
        try {
            db.cosmeticPurchaseDao().insert(
                com.focusdivergent.app.data.local.entity.CosmeticPurchaseEntity(cosmeticId = "PROP_DRAGON", price = 50),
            )
            val repository = CosmeticRepositoryImpl(db.cosmeticPurchaseDao())
            repository.add(CosmeticPurchase(Cosmetic.PROP_PLANT, 5))

            assertEquals(listOf(CosmeticPurchase(Cosmetic.PROP_PLANT, 5)), repository.observePurchases().first())
        } finally {
            db.close()
        }
    }
}
