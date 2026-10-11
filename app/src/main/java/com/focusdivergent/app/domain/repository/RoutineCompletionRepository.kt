package com.focusdivergent.app.domain.repository

import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Qué rutinas se han hecho cada día. Las rutinas se repiten, así que se marcan por día y no con un estado único. */
interface RoutineCompletionRepository {

    /** Ids de las rutinas hechas en [day]. */
    fun observeCompletedOn(day: LocalDate): Flow<Set<Long>>

    /** Los días en que se hizo al menos una rutina, sin repetir y de más antiguo a más reciente. */
    fun observeCompletedDays(): Flow<List<LocalDate>>

    suspend fun getCompletedDays(): List<LocalDate>

    /** Marca la rutina como hecha en [day]. No hace nada si ya lo estaba. */
    suspend fun markCompleted(taskId: Long, day: LocalDate, at: Instant)

    suspend fun unmarkCompleted(taskId: Long, day: LocalDate)
}
