package com.focusdivergent.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.focusdivergent.app.data.local.entity.RoutineCompletionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineCompletionDao {

    /** Ids de las rutinas marcadas como hechas en [day] (días desde 1970). */
    @Query("SELECT task_id FROM routine_completions WHERE day = :day")
    fun observeTaskIdsOn(day: Long): Flow<List<Long>>

    /** Los días (desde 1970) en que se hizo alguna rutina, sin repetir y en orden. */
    @Query("SELECT DISTINCT day FROM routine_completions ORDER BY day")
    fun observeCompletedDays(): Flow<List<Long>>

    @Query("SELECT DISTINCT day FROM routine_completions ORDER BY day")
    suspend fun getCompletedDays(): List<Long>

    /** Marcar dos veces el mismo día no duplica ni cambia la hora de la primera marca. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(completion: RoutineCompletionEntity)

    @Query("DELETE FROM routine_completions WHERE task_id = :taskId AND day = :day")
    suspend fun delete(taskId: Long, day: Long)
}
