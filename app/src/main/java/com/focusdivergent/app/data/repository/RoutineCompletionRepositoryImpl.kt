package com.focusdivergent.app.data.repository

import com.focusdivergent.app.data.local.dao.RoutineCompletionDao
import com.focusdivergent.app.data.local.entity.RoutineCompletionEntity
import com.focusdivergent.app.domain.repository.RoutineCompletionRepository
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoutineCompletionRepositoryImpl @Inject constructor(
    private val dao: RoutineCompletionDao,
) : RoutineCompletionRepository {

    override fun observeCompletedOn(day: LocalDate): Flow<Set<Long>> =
        dao.observeTaskIdsOn(day.toEpochDay()).map { it.toSet() }

    override fun observeCompletedDays(): Flow<List<LocalDate>> =
        dao.observeCompletedDays().map { days -> days.map(LocalDate::ofEpochDay) }

    override suspend fun getCompletedDays(): List<LocalDate> =
        dao.getCompletedDays().map(LocalDate::ofEpochDay)

    override suspend fun markCompleted(taskId: Long, day: LocalDate, at: Instant) =
        dao.insert(RoutineCompletionEntity(taskId = taskId, day = day.toEpochDay(), completedAt = at.toEpochMilli()))

    override suspend fun unmarkCompleted(taskId: Long, day: LocalDate) = dao.delete(taskId, day.toEpochDay())
}
