package com.focusdivergent.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.focusdivergent.app.data.local.entity.TimeExtensionEntity

@Dao
interface TimeExtensionDao {

    /** Ampliaciones de una app concedidas desde [since] (epoch ms, incluido). */
    @Query("SELECT * FROM time_extensions WHERE package_name = :packageName AND granted_at >= :since ORDER BY granted_at ASC")
    suspend fun getSince(packageName: String, since: Long): List<TimeExtensionEntity>

    @Insert
    suspend fun insert(extension: TimeExtensionEntity): Long

    /** Solo lo usa el receptor de depuración para repetir pruebas; la app nunca borra ampliaciones. */
    @Query("DELETE FROM time_extensions WHERE package_name = :packageName")
    suspend fun deleteFor(packageName: String)
}
