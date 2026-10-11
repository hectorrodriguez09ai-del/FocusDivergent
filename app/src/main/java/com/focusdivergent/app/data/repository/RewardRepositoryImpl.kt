package com.focusdivergent.app.data.repository

import com.focusdivergent.app.data.local.dao.XpEventDao
import com.focusdivergent.app.data.local.entity.XpEventEntity
import com.focusdivergent.app.domain.repository.RewardRepository
import com.focusdivergent.app.domain.reward.DayReward
import com.focusdivergent.app.domain.reward.RewardCause
import com.focusdivergent.app.domain.reward.RewardEvent
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RewardRepositoryImpl @Inject constructor(
    private val dao: XpEventDao,
) : RewardRepository {

    override fun observeDailyTotals(): Flow<List<DayReward>> =
        dao.observeDailyTotals().map { rows ->
            rows.map { DayReward(day = LocalDate.ofEpochDay(it.day), xp = it.xp, coins = it.coins, minutes = it.minutes) }
        }

    override suspend fun record(event: RewardEvent) = dao.insert(
        XpEventEntity(
            cause = event.cause.name,
            refId = event.refId,
            day = event.day.toEpochDay(),
            xp = event.xp,
            coins = event.coins,
            minutes = event.minutes,
        ),
    )

    override suspend fun retract(cause: RewardCause, refId: Long, day: LocalDate?) =
        if (day == null) dao.delete(cause.name, refId) else dao.deleteOn(cause.name, refId, day.toEpochDay())
}
