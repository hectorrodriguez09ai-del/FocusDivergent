package com.focusdivergent.app.data.repository

import com.focusdivergent.app.data.local.dao.VaultNodeDao
import com.focusdivergent.app.data.mapper.toDomain
import com.focusdivergent.app.data.mapper.toEntity
import com.focusdivergent.app.data.mapper.toTagList
import com.focusdivergent.app.domain.model.VaultNode
import com.focusdivergent.app.domain.repository.VaultRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class VaultRepositoryImpl @Inject constructor(
    private val dao: VaultNodeDao,
) : VaultRepository {

    override fun observeChildren(parentId: Long?): Flow<List<VaultNode>> =
        dao.observeChildren(parentId).map { list -> list.map { it.toDomain() } }

    override fun observeFolders(): Flow<List<VaultNode>> =
        dao.observeFolders().map { list -> list.map { it.toDomain() } }

    // Las más usadas primero; a igualdad, por orden alfabético. «Música» y «música» cuentan como la misma.
    override fun observeTags(): Flow<List<String>> = dao.observeTagColumns().map { columns ->
        columns.flatMap { it.toTagList() }
            .groupBy { it.lowercase() }
            .values
            .sortedWith(compareByDescending<List<String>> { it.size }.thenBy { it.first().lowercase() })
            .map { it.first() }
    }

    override suspend fun getById(id: Long): VaultNode? = dao.getById(id)?.toDomain()

    override fun search(query: String): Flow<List<VaultNode>> {
        val matchQuery = query.toFtsMatchQuery() ?: return flowOf(emptyList())
        return dao.search(matchQuery).map { list -> list.map { it.toDomain() } }
    }

    // El upsert de Room devuelve -1 cuando actualiza una fila que ya existía: en ese caso el id es el del nodo.
    override suspend fun save(node: VaultNode): Long {
        val rowId = dao.upsert(node.toEntity())
        return if (rowId == -1L) node.id else rowId
    }

    override suspend fun delete(node: VaultNode) = dao.delete(node.toEntity())
}
