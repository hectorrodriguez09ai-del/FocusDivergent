package com.focusdivergent.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.focusdivergent.app.data.local.entity.StepTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StepTemplateDao {

    @Query("SELECT * FROM step_templates ORDER BY name COLLATE NOCASE ASC, id ASC")
    fun observeAll(): Flow<List<StepTemplateEntity>>

    @Query("SELECT * FROM step_templates")
    suspend fun getAll(): List<StepTemplateEntity>

    @Insert
    suspend fun insert(template: StepTemplateEntity): Long

    @Query("UPDATE step_templates SET name = :name, steps = :steps WHERE id = :id")
    suspend fun update(id: Long, name: String, steps: String)

    @Query("DELETE FROM step_templates WHERE id = :id")
    suspend fun delete(id: Long)
}
