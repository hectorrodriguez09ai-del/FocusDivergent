package com.focusdivergent.app.data.repository

import com.focusdivergent.app.data.local.dao.PendingLimitChangeDao
import com.focusdivergent.app.data.mapper.toDomain
import com.focusdivergent.app.data.mapper.toEntity
import com.focusdivergent.app.domain.model.PendingLimitChange
import com.focusdivergent.app.domain.repository.PendingLimitChangeRepository
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PendingLimitChangeRepositoryImpl @Inject constructor(
    private val dao: PendingLimitChangeDao,
) : PendingLimitChangeRepository {

    override fun observeAll(): Flow<List<PendingLimitChange>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun get(packageName: String): PendingLimitChange? = dao.get(packageName)?.toDomain()

    override suspend fun getDue(now: Instant): List<PendingLimitChange> =
        dao.getDue(now.toEpochMilli()).map { it.toDomain() }

    override suspend fun save(change: PendingLimitChange) = dao.upsert(change.toEntity())

    override suspend fun delete(packageName: String) = dao.delete(packageName)
}
