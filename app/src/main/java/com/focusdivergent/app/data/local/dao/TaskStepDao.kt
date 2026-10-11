package com.focusdivergent.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.focusdivergent.app.data.local.entity.TaskStepEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskStepDao {

    @Query("SELECT * FROM task_steps ORDER BY task_id ASC, position ASC, id ASC")
    fun observeAll(): Flow<List<TaskStepEntity>>

    @Query("SELECT * FROM task_steps WHERE task_id = :taskId ORDER BY position ASC, id ASC")
    fun observeForTask(taskId: Long): Flow<List<TaskStepEntity>>

    @Query("SELECT * FROM task_steps WHERE task_id = :taskId ORDER BY position ASC, id ASC")
    suspend fun getForTask(taskId: Long): List<TaskStepEntity>

    @Query("SELECT * FROM task_steps WHERE id = :id")
    suspend fun getById(id: Long): TaskStepEntity?

    @Query("SELECT MAX(position) FROM task_steps WHERE task_id = :taskId")
    suspend fun maxPosition(taskId: Long): Int?

    @Insert
    suspend fun insert(step: TaskStepEntity): Long

    @Query("UPDATE task_steps SET text = :text WHERE id = :id")
    suspend fun rename(id: Long, text: String)

    @Query("UPDATE task_steps SET done = :done WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean)

    @Query("UPDATE task_steps SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int)

    @Query("DELETE FROM task_steps WHERE id = :id")
    suspend fun delete(id: Long)
}
