package com.focusdivergent.app.domain.repository

import com.focusdivergent.app.domain.model.BrainDump
import kotlinx.coroutines.flow.Flow

interface BrainDumpRepository {

    /** Bandeja de entrada: ideas sin procesar, la más reciente primero. */
    fun observeUnprocessed(): Flow<List<BrainDump>>

    /** Guarda una idea nueva y devuelve su id. */
    suspend fun add(dump: BrainDump): Long

    suspend fun markProcessed(id: Long)

    suspend fun delete(dump: BrainDump)
}
