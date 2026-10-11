package com.focusdivergent.app.data.repository

import com.focusdivergent.app.data.local.dao.AppLimitDao
import com.focusdivergent.app.data.mapper.toDomain
import com.focusdivergent.app.data.mapper.toEntity
import com.focusdivergent.app.domain.model.AppLimit
import com.focusdivergent.app.domain.repository.AppLimitRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppLimitRepositoryImpl @Inject constructor(
    private val dao: AppLimitDao,
) : AppLimitRepository {

    override fun observeAll(): Flow<List<AppLimit>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getByPackage(packageName: String): AppLimit? =
        dao.getByPackage(packageName)?.toDomain()

    override suspend fun save(limit: AppLimit) = dao.upsert(limit.toEntity())

    override suspend fun delete(limit: AppLimit) = dao.delete(limit.toEntity())
}
