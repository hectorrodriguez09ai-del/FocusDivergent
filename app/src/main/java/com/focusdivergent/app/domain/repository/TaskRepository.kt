package com.focusdivergent.app.domain.repository

import com.focusdivergent.app.domain.model.EnergyLevel
import com.focusdivergent.app.domain.model.Task
import com.focusdivergent.app.domain.model.TaskStatus
import com.focusdivergent.app.domain.model.TaskType
import java.time.Instant
import kotlinx.coroutines.flow.Flow

interface TaskRepository {

    /** Todas las actividades: primero las que tienen hora, por orden cronológico. */
    fun observeAll(): Flow<List<Task>>

    /** Filtro por energía disponible (RF-2.1). */
    fun observeByEnergy(energyLevel: EnergyLevel): Flow<List<Task>>

    fun observeByType(type: TaskType): Flow<List<Task>>

    /** Actividades de un tipo que empiezan entre [from] y [to], ambos incluidos. */
    fun observeByTypeBetween(type: TaskType, from: Instant, to: Instant): Flow<List<Task>>

    suspend fun getById(id: Long): Task?

    /**
     * Obligaciones pendientes que empiezan después de [from] y hasta [to] incluido, las más próximas primero.
     * Una que empieza justo en [from] ya ha comenzado y no cuenta.
     */
    suspend fun getPendingObligationsStartingBetween(from: Instant, to: Instant): List<Task>

    /** La primera Obligación pendiente que empieza estrictamente después de [after], si hay alguna. */
    suspend fun getNextPendingObligationStartingAfter(after: Instant): Task?

    /** Crea o actualiza la actividad y devuelve su id definitivo. */
    suspend fun save(task: Task): Long

    /** Cambia el estado. [completedAt] debe ir informado al pasar a hecha y nulo en cualquier otro caso. */
    suspend fun updateStatus(id: Long, status: TaskStatus, completedAt: Instant? = null)

    suspend fun delete(task: Task)
}
