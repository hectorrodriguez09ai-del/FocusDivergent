package com.focusdivergent.app.domain.repository

import com.focusdivergent.app.domain.model.UserStats
import kotlinx.coroutines.flow.Flow

interface UserStatsRepository {

    /** Progreso guardado. Si todavía no hay datos, emite los valores iniciales. */
    fun observe(): Flow<UserStats>

    /** Sube la mejor racha a [days] si es mayor que la guardada. Nunca la baja. */
    suspend fun raiseBestStreak(days: Int)
}
