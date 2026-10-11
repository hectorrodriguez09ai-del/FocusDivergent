package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.model.UserStats
import com.focusdivergent.app.domain.repository.CosmeticRepository
import com.focusdivergent.app.domain.repository.RewardRepository
import com.focusdivergent.app.domain.repository.UserStatsRepository
import com.focusdivergent.app.domain.reward.CosmeticPurchase
import com.focusdivergent.app.domain.reward.DayReward
import com.focusdivergent.app.domain.reward.MascotSelection
import com.focusdivergent.app.domain.reward.MascotSettings
import com.focusdivergent.app.domain.reward.RewardCause
import com.focusdivergent.app.domain.reward.RewardEvent
import com.focusdivergent.app.domain.reward.RewardLedger
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/** El libro de XP en memoria, con la misma regla de unicidad (causa, actividad y día) que el índice de Room. */
class FakeRewards : RewardRepository {
    private val state = MutableStateFlow<List<RewardEvent>>(emptyList())
    val events: List<RewardEvent> get() = state.value

    override fun observeDailyTotals(): Flow<List<DayReward>> = state.map { list ->
        list.groupBy { it.day }.map { (day, events) -> DayReward(day, events.sumOf { it.xp }, events.sumOf { it.coins }, events.sumOf { it.minutes }) }
    }

    override suspend fun record(event: RewardEvent) {
        if (events.none { it.cause == event.cause && it.refId == event.refId && it.day == event.day }) {
            state.value = events + event
        }
    }

    override suspend fun retract(cause: RewardCause, refId: Long, day: LocalDate?) {
        state.value = events.filterNot { it.cause == cause && it.refId == refId && (day == null || it.day == day) }
    }

    fun totalXp() = events.sumOf { it.xp }
}

class FakeCosmetics(initial: List<CosmeticPurchase> = emptyList()) : CosmeticRepository {
    private val state = MutableStateFlow(initial)
    val purchases: List<CosmeticPurchase> get() = state.value

    override fun observePurchases(): Flow<List<CosmeticPurchase>> = state

    override suspend fun add(purchase: CosmeticPurchase) {
        if (purchases.none { it.cosmetic == purchase.cosmetic }) state.value = purchases + purchase
    }
}

class FakeUserStats(best: Int = 0) : UserStatsRepository {
    private val state = MutableStateFlow(UserStats(bestStreak = best))
    val bestStreak: Int get() = state.value.bestStreak

    override fun observe(): Flow<UserStats> = state

    override suspend fun raiseBestStreak(days: Int) {
        if (days > bestStreak) state.value = UserStats(bestStreak = days)
    }
}

class FakeMascotSettings(initial: MascotSelection = MascotSelection()) : MascotSettings {
    private val state = MutableStateFlow(initial)
    override val selection: StateFlow<MascotSelection> = state

    override fun update(change: (MascotSelection) -> MascotSelection) {
        state.value = change(state.value)
    }
}

/** Un [RewardLedger] listo para usar en los tests, con las rutinas hechas que se le pasen. */
fun testLedger(
    completions: FakeRoutineCompletions = FakeRoutineCompletions(),
    rewards: FakeRewards = FakeRewards(),
    stats: FakeUserStats = FakeUserStats(),
    clock: Clock = TEST_CLOCK,
) = RewardLedger(rewards, completions, stats, clock)
