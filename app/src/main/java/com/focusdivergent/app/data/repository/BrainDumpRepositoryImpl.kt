package com.focusdivergent.app.data.repository

import com.focusdivergent.app.data.local.dao.BrainDumpDao
import com.focusdivergent.app.data.mapper.toDomain
import com.focusdivergent.app.data.mapper.toEntity
import com.focusdivergent.app.domain.model.BrainDump
import com.focusdivergent.app.domain.repository.BrainDumpRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BrainDumpRepositoryImpl @Inject constructor(
    private val dao: BrainDumpDao,
) : BrainDumpRepository {

    override fun observeUnprocessed(): Flow<List<BrainDump>> =
        dao.observeUnprocessed().map { list -> list.map { it.toDomain() } }

    override suspend fun add(dump: BrainDump): Long = dao.insert(dump.toEntity())

    override suspend fun markProcessed(id: Long) = dao.markProcessed(id)

    override suspend fun delete(dump: BrainDump) = dao.delete(dump.toEntity())
}
