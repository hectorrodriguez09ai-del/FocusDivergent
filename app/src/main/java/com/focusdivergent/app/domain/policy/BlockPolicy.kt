package com.focusdivergent.app.domain.policy

import com.focusdivergent.app.domain.model.AppLimit
import com.focusdivergent.app.domain.model.BlockReason

/** Regla de RF-1.1: cuándo una app con límite debe bloquearse. */
object BlockPolicy {

    private const val MILLIS_PER_MINUTE = 60_000L

    /**
     * Devuelve el motivo del bloqueo, o nulo si la app todavía se puede usar.
     *
     * @param extraMinutes minutos ganados hoy con la fricción matemática, que se suman al límite diario.
     */
    fun evaluate(limit: AppLimit, usedMillis: Long, extraMinutes: Int = 0): BlockReason? {
        if (limit.isStrictlyBlocked) return BlockReason.StrictlyBlocked

        val allowedMillis = (limit.dailyMinutesAllowed + extraMinutes) * MILLIS_PER_MINUTE
        return if (usedMillis >= allowedMillis) {
            BlockReason.LimitReached(usedMillis = usedMillis, allowedMillis = allowedMillis)
        } else {
            null
        }
    }
}
