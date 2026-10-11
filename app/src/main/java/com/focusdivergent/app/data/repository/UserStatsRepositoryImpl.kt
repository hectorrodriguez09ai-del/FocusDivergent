package com.focusdivergent.app.data.repository

import com.focusdivergent.app.data.local.dao.UserStatsDao
import com.focusdivergent.app.data.mapper.toDomain
import com.focusdivergent.app.domain.model.UserStats
import com.focusdivergent.app.domain.repository.UserStatsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserStatsRepositoryImpl @Inject constructor(
    private val dao: UserStatsDao,
) : UserStatsRepository {

    override fun observe(): Flow<UserStats> =
        dao.observe().map { it?.toDomain() ?: UserStats() }

    override suspend fun raiseBestStreak(days: Int) = dao.raiseBestStreak(days)
}
