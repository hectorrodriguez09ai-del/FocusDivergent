package com.focusdivergent.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.focusdivergent.app.data.local.entity.XpEventEntity
import kotlinx.coroutines.flow.Flow

/** Lo ganado en un día, sumando todos sus movimientos. */
data class DayTotalRow(val day: Long, val xp: Int, val coins: Int, val minutes: Int)

@Dao
interface XpEventDao {

    @Query("SELECT day, SUM(xp) AS xp, SUM(coins) AS coins, SUM(minutes) AS minutes FROM xp_events GROUP BY day")
    fun observeDailyTotals(): Flow<List<DayTotalRow>>

    /** Un movimiento por causa, actividad y día: si ya existe, se ignora. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: XpEventEntity)

    @Query("DELETE FROM xp_events WHERE cause = :cause AND ref_id = :refId")
    suspend fun delete(cause: String, refId: Long)

    @Query("DELETE FROM xp_events WHERE cause = :cause AND ref_id = :refId AND day = :day")
    suspend fun deleteOn(cause: String, refId: Long, day: Long)
}
