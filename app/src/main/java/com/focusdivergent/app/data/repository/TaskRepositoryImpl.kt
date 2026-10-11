package com.focusdivergent.app.data.repository

import com.focusdivergent.app.data.local.dao.TaskDao
import com.focusdivergent.app.data.mapper.toDomain
import com.focusdivergent.app.data.mapper.toEntity
import com.focusdivergent.app.domain.model.EnergyLevel
import com.focusdivergent.app.domain.model.Task
import com.focusdivergent.app.domain.model.TaskStatus
import com.focusdivergent.app.domain.model.TaskType
import com.focusdivergent.app.domain.repository.TaskRepository
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TaskRepositoryImpl @Inject constructor(
    private val dao: TaskDao,
) : TaskRepository {

    override fun observeAll(): Flow<List<Task>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeByEnergy(energyLevel: EnergyLevel): Flow<List<Task>> =
        dao.observeByEnergy(energyLevel).map { list -> list.map { it.toDomain() } }

    override fun observeByType(type: TaskType): Flow<List<Task>> =
        dao.observeByType(type).map { list -> list.map { it.toDomain() } }

    override fun observeByTypeBetween(type: TaskType, from: Instant, to: Instant): Flow<List<Task>> =
        dao.observeByTypeBetween(type, from.toEpochMilli(), to.toEpochMilli())
            .map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: Long): Task? = dao.getById(id)?.toDomain()

    override suspend fun getPendingObligationsStartingBetween(from: Instant, to: Instant): List<Task> =
        dao.getStartingBetween(TaskType.OBLIGATION, TaskStatus.PENDING, from.toEpochMilli(), to.toEpochMilli())
            .map { it.toDomain() }

    override suspend fun getNextPendingObligationStartingAfter(after: Instant): Task? =
        dao.getFirstStartingAfter(TaskType.OBLIGATION, TaskStatus.PENDING, after.toEpochMilli())?.toDomain()

    // El upsert de Room devuelve -1 cuando actualiza una fila que ya existía: en ese caso el id es el de la tarea.
    override suspend fun save(task: Task): Long {
        val rowId = dao.upsert(task.toEntity())
        return if (rowId == -1L) task.id else rowId
    }

    override suspend fun updateStatus(id: Long, status: TaskStatus, completedAt: Instant?) =
        dao.updateStatus(id, status, completedAt?.toEpochMilli())

    override suspend fun delete(task: Task) = dao.delete(task.toEntity())
}
