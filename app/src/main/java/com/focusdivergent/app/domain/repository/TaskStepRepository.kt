package com.focusdivergent.app.domain.repository

import com.focusdivergent.app.domain.steps.StepTemplate
import com.focusdivergent.app.domain.steps.TaskStep
import kotlinx.coroutines.flow.Flow

/** Los micro-pasos de las tareas. Al borrar una tarea se van con ella. */
interface TaskStepRepository {

    /** Los pasos de todas las tareas, por tarea y orden. */
    fun observeAll(): Flow<List<TaskStep>>

    fun observeForTask(taskId: Long): Flow<List<TaskStep>>

    suspend fun getForTask(taskId: Long): List<TaskStep>

    suspend fun getById(id: Long): TaskStep?

    /** Añade pasos al final de la tarea y devuelve los ids nuevos. */
    suspend fun addAll(taskId: Long, texts: List<String>): List<Long>

    suspend fun rename(id: Long, text: String)

    suspend fun setDone(id: Long, done: Boolean)

    suspend fun delete(id: Long)

    /** Intercambia el paso con el de antes ([up]) o con el de después. No hace nada en los extremos. */
    suspend fun move(id: Long, up: Boolean)
}

/** Las plantillas de pasos que ha guardado el usuario. */
interface StepTemplateRepository {

    fun observeAll(): Flow<List<StepTemplate>>

    /** Guarda la plantilla; si ya había una con ese nombre (sin mirar mayúsculas), la sustituye. */
    suspend fun save(name: String, steps: List<String>)

    suspend fun delete(id: Long)
}
