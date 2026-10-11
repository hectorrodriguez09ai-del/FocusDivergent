package com.focusdivergent.app.domain.repository

import com.focusdivergent.app.domain.reward.DayReward
import com.focusdivergent.app.domain.reward.RewardCause
import com.focusdivergent.app.domain.reward.RewardEvent
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** El libro de XP y monedas: un movimiento por cada cosa que se hizo, de donde se calculan los totales. */
interface RewardRepository {

    /** Lo ganado cada día (sin topes), en el orden que sea. */
    fun observeDailyTotals(): Flow<List<DayReward>>

    /** Anota un movimiento. Si ya había uno de la misma causa, actividad y día, no hace nada. */
    suspend fun record(event: RewardEvent)

    /** Borra los movimientos de [cause] y [refId] (solo los de [day], si se indica). Es lo que hace deshacer. */
    suspend fun retract(cause: RewardCause, refId: Long, day: LocalDate? = null)
}
