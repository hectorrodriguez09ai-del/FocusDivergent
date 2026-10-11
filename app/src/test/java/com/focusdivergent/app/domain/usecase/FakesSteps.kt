package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.repository.StepTemplateRepository
import com.focusdivergent.app.domain.repository.TaskStepRepository
import com.focusdivergent.app.domain.steps.StepTemplate
import com.focusdivergent.app.domain.steps.TaskStep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Micro-pasos en memoria, con las mismas reglas de orden que el repositorio de Room. */
class FakeTaskSteps(initial: List<TaskStep> = emptyList()) : TaskStepRepository {
    private val state = MutableStateFlow(initial)
    val items: List<TaskStep> get() = state.value

    private fun ordered(taskId: Long) = items.filter { it.taskId == taskId }.sortedWith(compareBy({ it.position }, { it.id }))

    override fun observeAll(): Flow<List<TaskStep>> = state.map { list -> list.sortedWith(compareBy({ it.taskId }, { it.position }, { it.id })) }
    override fun observeForTask(taskId: Long): Flow<List<TaskStep>> = state.map { ordered(taskId) }
    override suspend fun getForTask(taskId: Long): List<TaskStep> = ordered(taskId)
    override suspend fun getById(id: Long): TaskStep? = items.firstOrNull { it.id == id }

    override suspend fun addAll(taskId: Long, texts: List<String>): List<Long> {
        var position = (items.filter { it.taskId == taskId }.maxOfOrNull { it.position } ?: -1) + 1
        var nextId = (items.maxOfOrNull { it.id } ?: 0L) + 1
        val added = texts.map { TaskStep(id = nextId++, taskId = taskId, position = position++, text = it) }
        state.value = items + added
        return added.map { it.id }
    }

    override suspend fun rename(id: Long, text: String) {
        state.value = items.map { if (it.id == id) it.copy(text = text) else it }
    }

    override suspend fun setDone(id: Long, done: Boolean) {
        state.value = items.map { if (it.id == id) it.copy(done = done) else it }
    }

    override suspend fun delete(id: Long) {
        state.value = items.filterNot { it.id == id }
    }

    override suspend fun move(id: Long, up: Boolean) {
        val step = getById(id) ?: return
        val list = ordered(step.taskId).toMutableList()
        val index = list.indexOfFirst { it.id == id }
        val target = if (up) index - 1 else index + 1
        if (target !in list.indices) return
        list[index] = list[target].also { list[target] = list[index] }
        val renumbered = list.mapIndexed { position, row -> row.copy(position = position) }.associateBy { it.id }
        state.value = items.map { renumbered[it.id] ?: it }
    }
}

class FakeStepTemplates(initial: List<StepTemplate> = emptyList()) : StepTemplateRepository {
    private val state = MutableStateFlow(initial)
    val items: List<StepTemplate> get() = state.value

    override fun observeAll(): Flow<List<StepTemplate>> = state

    override suspend fun save(name: String, steps: List<String>) {
        val existing = items.firstOrNull { it.name.equals(name, ignoreCase = true) }
        state.value = if (existing != null) {
            items.map { if (it.id == existing.id) it.copy(name = name, steps = steps) else it }
        } else {
            items + StepTemplate(id = (items.maxOfOrNull { it.id } ?: 0L) + 1, name = name, steps = steps)
        }
    }

    override suspend fun delete(id: Long) {
        state.value = items.filterNot { it.id == id }
    }
}
