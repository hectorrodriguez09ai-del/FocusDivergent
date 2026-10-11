package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.repository.CosmeticRepository
import com.focusdivergent.app.domain.repository.RewardRepository
import com.focusdivergent.app.domain.repository.RoutineCompletionRepository
import com.focusdivergent.app.domain.repository.UserStatsRepository
import com.focusdivergent.app.domain.reward.Cosmetic
import com.focusdivergent.app.domain.reward.CosmeticKind
import com.focusdivergent.app.domain.reward.CosmeticPurchase
import com.focusdivergent.app.domain.reward.DayReward
import com.focusdivergent.app.domain.reward.LevelProgress
import com.focusdivergent.app.domain.reward.Levels
import com.focusdivergent.app.domain.reward.MascotSelection
import com.focusdivergent.app.domain.reward.MascotSettings
import com.focusdivergent.app.domain.reward.MascotStages
import com.focusdivergent.app.domain.reward.RewardTotals
import com.focusdivergent.app.domain.reward.StreakCalculator
import com.focusdivergent.app.domain.reward.StreakInfo
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.max
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Todo lo que enseñan la mascota y «Hoy» del progreso del usuario.
 *
 * @property coins el saldo de monedas de enfoque. Nunca es negativo.
 * @property owned los cosméticos que se pueden poner: los comprados y el fondo gratuito.
 * @property selection lo que lleva puesto, siempre algo que posee.
 * @property stage la etapa de evolución de la mascota, de 1 a [MascotStages.COUNT].
 */
data class Progress(
    val level: LevelProgress,
    val stage: Int,
    val xpToday: Int,
    val coins: Int,
    val streak: StreakInfo,
    val owned: Set<Cosmetic>,
    val selection: MascotSelection,
)

class ObserveProgressUseCase @Inject constructor(
    private val rewards: RewardRepository,
    private val completions: RoutineCompletionRepository,
    private val cosmetics: CosmeticRepository,
    private val stats: UserStatsRepository,
    private val settings: MascotSettings,
) {

    operator fun invoke(today: LocalDate): Flow<Progress> = combine(
        rewards.observeDailyTotals(),
        completions.observeCompletedDays(),
        cosmetics.observePurchases(),
        stats.observe(),
        settings.selection,
    ) { days, routineDays, purchases, userStats, selection ->
        val level = Levels.of(RewardTotals.xp(days))
        val computed = StreakCalculator.compute(routineDays, today)
        val owned = purchases.map { it.cosmetic }.toSet() + Cosmetic.entries.filter { it.isFree }
        Progress(
            level = level,
            stage = MascotStages.stageOf(level.level),
            xpToday = RewardTotals.xpOn(days, today),
            coins = coinBalance(days, purchases),
            // La mejor racha guardada no baja aunque ya no queden las marcas con las que se consiguió.
            streak = computed.copy(best = max(computed.best, userStats.bestStreak)),
            owned = owned,
            selection = selection.ownedOnly(owned),
        )
    }
}

/** Monedas ganadas (con tope diario) menos las gastadas. Si deshacer una tarea dejara el saldo bajo cero, se queda en cero. */
internal fun coinBalance(days: List<DayReward>, purchases: List<CosmeticPurchase>): Int =
    max(0, RewardTotals.coins(days) - purchases.sumOf { it.price })

/** Si la selección guardada menciona algo que ya no se posee, vuelve a lo gratuito en vez de enseñarlo. */
private fun MascotSelection.ownedOnly(owned: Set<Cosmetic>) = copy(
    scenery = scenery.takeIf { it in owned } ?: Cosmetic.SCENERY_CALM,
    prop = prop?.takeIf { it in owned },
)

enum class PurchaseResult { BOUGHT, ALREADY_OWNED, NOT_ENOUGH_COINS }

/** Compra un cosmético con monedas y se lo pone. Comprobar el saldo y anotar la compra va junto, bajo un cerrojo. */
class BuyCosmeticUseCase @Inject constructor(
    private val rewards: RewardRepository,
    private val cosmetics: CosmeticRepository,
    private val settings: MascotSettings,
) {

    suspend operator fun invoke(cosmetic: Cosmetic): PurchaseResult = lock.withLock {
        val purchases = cosmetics.observePurchases().first()
        if (cosmetic.isFree || purchases.any { it.cosmetic == cosmetic }) return PurchaseResult.ALREADY_OWNED
        val balance = coinBalance(rewards.observeDailyTotals().first(), purchases)
        if (balance < cosmetic.price) return PurchaseResult.NOT_ENOUGH_COINS
        cosmetics.add(CosmeticPurchase(cosmetic, cosmetic.price))
        settings.update { it.wearing(cosmetic) }
        PurchaseResult.BOUGHT
    }

    private companion object {
        val lock = Mutex()
    }
}

/** Pone o quita un cosmético que ya se posee. */
class EquipCosmeticUseCase @Inject constructor(
    private val cosmetics: CosmeticRepository,
    private val settings: MascotSettings,
) {

    /**
     * Pone [cosmetic]; si es un objeto que ya se lleva, lo quita. Un fondo no se quita: se cambia por otro.
     * @return `false` si no se posee y no se hizo nada.
     */
    suspend operator fun invoke(cosmetic: Cosmetic): Boolean {
        val owned = cosmetic.isFree || cosmetics.observePurchases().first().any { it.cosmetic == cosmetic }
        if (!owned) return false
        settings.update { current ->
            if (cosmetic.kind == CosmeticKind.PROP && current.prop == cosmetic) current.copy(prop = null) else current.wearing(cosmetic)
        }
        return true
    }
}
