package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.model.BrainDump
import com.focusdivergent.app.domain.model.VaultNode
import com.focusdivergent.app.domain.repository.BrainDumpRepository
import com.focusdivergent.app.domain.repository.VaultRepository
import com.focusdivergent.app.domain.settings.CaptureTileOffer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/** Bandeja de entrada en memoria, con las mismas reglas que la de Room (sin procesar, la más reciente primero). */
class FakeBrainDump(initial: List<BrainDump> = emptyList()) : BrainDumpRepository {
    private val state = MutableStateFlow(initial)
    val items: List<BrainDump> get() = state.value

    override fun observeUnprocessed(): Flow<List<BrainDump>> =
        state.map { list -> list.filter { !it.isProcessed }.sortedByDescending { it.timestamp } }

    override suspend fun add(dump: BrainDump): Long {
        val id = if (dump.id != 0L) dump.id else (items.maxOfOrNull { it.id } ?: 0L) + 1
        state.value = items.filterNot { it.id == id } + dump.copy(id = id)
        return id
    }

    override suspend fun markProcessed(id: Long) {
        state.value = items.map { if (it.id == id) it.copy(isProcessed = true) else it }
    }

    override suspend fun delete(dump: BrainDump) {
        state.value = items.filterNot { it.id == dump.id }
    }
}

/** Bóveda en memoria: solo lo que necesitan los tests de la bandeja (hijos de un nodo, guardar y borrar). */
class FakeVault(initial: List<VaultNode> = emptyList()) : VaultRepository {
    private val state = MutableStateFlow(initial)
    val items: List<VaultNode> get() = state.value

    override fun observeChildren(parentId: Long?): Flow<List<VaultNode>> =
        // Como la consulta real: por título, sin distinguir mayúsculas.
        state.map { list -> list.filter { it.parentId == parentId }.sortedBy { it.title.lowercase() } }

    override fun observeFolders(): Flow<List<VaultNode>> =
        state.map { list -> list.filter { it.isFolder }.sortedBy { it.title.lowercase() } }

    override fun observeTags(): Flow<List<String>> = state.map { list ->
        list.flatMap { it.tags }.groupBy { it.lowercase() }.values
            .sortedWith(compareByDescending<List<String>> { it.size }.thenBy { it.first().lowercase() })
            .map { it.first() }
    }

    override suspend fun getById(id: Long): VaultNode? = items.firstOrNull { it.id == id }

    override fun search(query: String): Flow<List<VaultNode>> =
        state.map { list ->
            list.filter {
                query.isNotBlank() &&
                    (it.title.contains(query, ignoreCase = true) || it.markdownContent.orEmpty().contains(query, ignoreCase = true))
            }
        }

    override suspend fun save(node: VaultNode): Long {
        val id = if (node.id != 0L) node.id else (items.maxOfOrNull { it.id } ?: 0L) + 1
        state.value = items.filterNot { it.id == id } + node.copy(id = id)
        return id
    }

    // Como la base de datos real: borrar una carpeta borra todo lo que contiene, a cualquier profundidad.
    override suspend fun delete(node: VaultNode) {
        val doomed = mutableSetOf(node.id)
        do {
            val before = doomed.size
            doomed += items.filter { it.parentId in doomed }.map { it.id }
        } while (doomed.size != before)
        state.value = items.filterNot { it.id in doomed }
    }
}

class FakeCaptureTileOffer(pending: Boolean = true) : CaptureTileOffer {
    private val state = MutableStateFlow(pending)
    override val pending: StateFlow<Boolean> = state

    override fun markHandled() {
        state.value = false
    }
}
