package com.focusdivergent.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.focusdivergent.app.data.local.entity.TaskEntity
import com.focusdivergent.app.domain.model.EnergyLevel
import com.focusdivergent.app.domain.model.TaskStatus
import com.focusdivergent.app.domain.model.TaskType
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    /** Todas las actividades: primero las que tienen hora, por orden cronológico. */
    @Query("SELECT * FROM tasks ORDER BY start_time IS NULL, start_time ASC, id ASC")
    fun observeAll(): Flow<List<TaskEntity>>

    /** Filtro por energía disponible (RF-2.1). */
    @Query("SELECT * FROM tasks WHERE energy_level = :energyLevel ORDER BY start_time IS NULL, start_time ASC, id ASC")
    fun observeByEnergy(energyLevel: EnergyLevel): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE type = :type ORDER BY start_time IS NULL, start_time ASC, id ASC")
    fun observeByType(type: TaskType): Flow<List<TaskEntity>>

    /**
     * Actividades de un tipo cuya hora de inicio cae en el rango (epoch ms, ambos extremos incluidos).
     * La usará el motor de bloqueo para anticipar las Obligaciones (RF-1.2).
     */
    @Query("SELECT * FROM tasks WHERE type = :type AND start_time BETWEEN :from AND :to ORDER BY start_time ASC")
    fun observeByTypeBetween(type: TaskType, from: Long, to: Long): Flow<List<TaskEntity>>

    /** Actividades de un tipo y estado que empiezan después de [from] y hasta [to] incluido (epoch ms). */
    @Query(
        "SELECT * FROM tasks WHERE type = :type AND status = :status AND start_time > :from AND start_time <= :to " +
            "ORDER BY start_time ASC, id ASC",
    )
    suspend fun getStartingBetween(type: TaskType, status: TaskStatus, from: Long, to: Long): List<TaskEntity>

    /** La primera actividad de un tipo y estado que empieza estrictamente después de [after] (epoch ms). */
    @Query(
        "SELECT * FROM tasks WHERE type = :type AND status = :status AND start_time > :after " +
            "ORDER BY start_time ASC, id ASC LIMIT 1",
    )
    suspend fun getFirstStartingAfter(type: TaskType, status: TaskStatus, after: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    /** Inserta o actualiza. Devuelve el id de la fila nueva, o -1 si fue una actualización. */
    @Upsert
    suspend fun upsert(task: TaskEntity): Long

    /** Cambia el estado y, con él, el momento en que se completó ([completedAt] nulo si deja de estar hecha). */
    @Query("UPDATE tasks SET status = :status, completed_at = :completedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: TaskStatus, completedAt: Long?)

    @Delete
    suspend fun delete(task: TaskEntity)
}
