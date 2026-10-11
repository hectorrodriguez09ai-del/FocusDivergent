package com.focusdivergent.app.domain.repository

import com.focusdivergent.app.domain.model.PendingLimitChange
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** Cambios de límite que esperan a medianoche. Como mucho uno por app: un cambio nuevo sustituye al anterior. */
interface PendingLimitChangeRepository {

    fun observeAll(): Flow<List<PendingLimitChange>>

    suspend fun get(packageName: String): PendingLimitChange?

    /** Los que ya deberían haber entrado en vigor: [PendingLimitChange.appliesAt] es [now] o anterior. */
    suspend fun getDue(now: Instant): List<PendingLimitChange>

    /** Guarda el cambio, sustituyendo al que hubiera pendiente para esa app. */
    suspend fun save(change: PendingLimitChange)

    suspend fun delete(packageName: String)
}
