package com.focusdivergent.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.focusdivergent.app.data.local.entity.PendingLimitChangeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingLimitChangeDao {

    @Query("SELECT * FROM pending_limit_changes ORDER BY package_name ASC")
    fun observeAll(): Flow<List<PendingLimitChangeEntity>>

    @Query("SELECT * FROM pending_limit_changes WHERE package_name = :packageName")
    suspend fun get(packageName: String): PendingLimitChangeEntity?

    /** Los que ya han llegado a su hora (epoch ms). El servicio lo consulta en cada ciclo, así que es mínima. */
    @Query("SELECT * FROM pending_limit_changes WHERE applies_at <= :now")
    suspend fun getDue(now: Long): List<PendingLimitChangeEntity>

    @Upsert
    suspend fun upsert(change: PendingLimitChangeEntity)

    @Query("DELETE FROM pending_limit_changes WHERE package_name = :packageName")
    suspend fun delete(packageName: String)
}
