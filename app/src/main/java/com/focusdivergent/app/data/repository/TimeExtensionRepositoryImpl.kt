package com.focusdivergent.app.data.repository

import com.focusdivergent.app.data.local.dao.TimeExtensionDao
import com.focusdivergent.app.data.mapper.toDomain
import com.focusdivergent.app.data.mapper.toEntity
import com.focusdivergent.app.domain.model.TimeExtension
import com.focusdivergent.app.domain.repository.TimeExtensionRepository
import java.time.Instant
import javax.inject.Inject

class TimeExtensionRepositoryImpl @Inject constructor(
    private val dao: TimeExtensionDao,
) : TimeExtensionRepository {

    override suspend fun getSince(packageName: String, since: Instant): List<TimeExtension> =
        dao.getSince(packageName, since.toEpochMilli()).map { it.toDomain() }

    override suspend fun add(extension: TimeExtension): Long = dao.insert(extension.toEntity())
}
