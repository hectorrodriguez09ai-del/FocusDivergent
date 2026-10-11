package com.focusdivergent.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.focusdivergent.app.data.local.entity.UserStatsEntity
import kotlinx.coroutines.flow.Flow

/** Opera siempre sobre la fila única de `user_stats` (id = 1, ver [UserStatsEntity.SINGLETON_ID]). */
@Dao
interface UserStatsDao {

    @Query("SELECT * FROM user_stats WHERE id = 1")
    fun observe(): Flow<UserStatsEntity?>

    /** Solo escribe si [days] supera lo guardado: la mejor racha no baja. */
    @Query("UPDATE user_stats SET best_streak = :days WHERE id = 1 AND best_streak < :days")
    suspend fun raiseBestStreak(days: Int)
}
