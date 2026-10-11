package com.focusdivergent.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.focusdivergent.app.data.local.entity.AppLimitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppLimitDao {

    @Query("SELECT * FROM app_limits ORDER BY package_name ASC")
    fun observeAll(): Flow<List<AppLimitEntity>>

    /** Consulta puntual del límite de una app; es la que hará el servicio de bloqueo en cada cambio de ventana. */
    @Query("SELECT * FROM app_limits WHERE package_name = :packageName")
    suspend fun getByPackage(packageName: String): AppLimitEntity?

    @Upsert
    suspend fun upsert(limit: AppLimitEntity)

    @Delete
    suspend fun delete(limit: AppLimitEntity)
}
