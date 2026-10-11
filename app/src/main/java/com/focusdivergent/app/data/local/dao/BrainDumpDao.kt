package com.focusdivergent.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.focusdivergent.app.data.local.entity.BrainDumpEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BrainDumpDao {

    /** Bandeja de entrada: lo capturado y aún sin procesar, lo más reciente primero. */
    @Query("SELECT * FROM brain_dumps WHERE is_processed = 0 ORDER BY timestamp DESC")
    fun observeUnprocessed(): Flow<List<BrainDumpEntity>>

    @Insert
    suspend fun insert(dump: BrainDumpEntity): Long

    @Query("UPDATE brain_dumps SET is_processed = 1 WHERE id = :id")
    suspend fun markProcessed(id: Long)

    @Delete
    suspend fun delete(dump: BrainDumpEntity)
}
