package com.focusdivergent.app.data.repository

import com.focusdivergent.app.data.local.dao.TaskStepDao
import com.focusdivergent.app.data.local.entity.TaskStepEntity
import com.focusdivergent.app.domain.repository.TaskStepRepository
import com.focusdivergent.app.domain.steps.TaskStep
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TaskStepRepositoryImpl @Inject constructor(
    private val dao: TaskStepDao,
) : TaskStepRepository {

    override fun observeAll(): Flow<List<TaskStep>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeForTask(taskId: Long): Flow<List<TaskStep>> =
        dao.observeForTask(taskId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getForTask(taskId: Long): List<TaskStep> = dao.getForTask(taskId).map { it.toDomain() }

    override suspend fun getById(id: Long): TaskStep? = dao.getById(id)?.toDomain()

    override suspend fun addAll(taskId: Long, texts: List<String>): List<Long> {
        var position = (dao.maxPosition(taskId) ?: -1) + 1
        return texts.map { text -> dao.insert(TaskStepEntity(taskId = taskId, position = position++, text = text)) }
    }

    override suspend fun rename(id: Long, text: String) = dao.rename(id, text)

    override suspend fun setDone(id: Long, done: Boolean) = dao.setDone(id, done)

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun move(id: Long, up: Boolean) {
        val step = dao.getById(id) ?: return
        val ordered = dao.getForTask(step.taskId).toMutableList()
        val index = ordered.indexOfFirst { it.id == id }
        val target = if (up) index - 1 else index + 1
        if (target !in ordered.indices) return
        ordered[index] = ordered[target].also { ordered[target] = ordered[index] }
        // Se renumeran todos (0, 1, 2…): así el intercambio funciona aunque hubiera huecos o posiciones repetidas.
        ordered.forEachIndexed { position, row -> if (row.position != position) dao.setPosition(row.id, position) }
    }

    private fun TaskStepEntity.toDomain() = TaskStep(id = id, taskId = taskId, position = position, text = text, done = done)
}
