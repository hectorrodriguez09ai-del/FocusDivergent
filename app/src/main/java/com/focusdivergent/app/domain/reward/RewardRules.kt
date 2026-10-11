package com.focusdivergent.app.domain.reward

import com.focusdivergent.app.domain.model.EnergyLevel
import com.focusdivergent.app.domain.model.FocusLaunchSession
import com.focusdivergent.app.domain.model.Task
import com.focusdivergent.app.domain.timer.TimerPhaseKind
import com.focusdivergent.app.domain.timer.TimerPlan
import com.focusdivergent.app.domain.timer.TimerSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

/**
 * Cuánto vale cada cosa. Todos los números del sistema de XP están aquí, en un solo sitio, para poder ajustarlos
 * sin tocar nada más.
 *
 * Solo se premia hacer; no hay XP por crear, editar ni borrar, y nunca se resta.
 */
object RewardRules {
    const val TASK_XP = 10
    const val TASK_XP_HIGH_ENERGY = 15
    const val TASK_COINS = 1

    const val ROUTINE_XP = 8
    const val ROUTINE_COINS = 1

    /** Por cada tramo completo de [FOCUS_CHUNK_MINUTES] minutos de un bloque de enfoque. */
    const val FOCUS_XP_PER_CHUNK = 5
    const val FOCUS_CHUNK_MINUTES = 15

    /** Un bloque más corto que esto no cuenta como bloque de enfoque. */
    const val FOCUS_MIN_BLOCK_MINUTES = 10
    const val FOCUS_COINS_PER_BLOCK = 1

    const val LAUNCH_XP = 5

    /** Por cada micro-paso hecho. Pequeño a propósito: los pasos son lo más fácil de repetir. */
    const val STEP_XP = 2

    /** Por cada app cuyo límite se respetó durante el día. */
    const val LIMIT_RESPECTED_XP = 5

    /**
     * Tope diario. Evita «cultivar» XP con acciones repetidas y hace que un día flojo no se sienta pequeño al lado
     * de uno muy bueno.
     */
    const val DAILY_XP_CAP = 100
    const val DAILY_COIN_CAP = 10

    fun taskDone(task: Task, day: LocalDate) = RewardEvent(
        cause = RewardCause.TASK_DONE,
        refId = task.id,
        day = day,
        xp = if (task.energyLevel == EnergyLevel.HIGH) TASK_XP_HIGH_ENERGY else TASK_XP,
        coins = TASK_COINS,
    )

    fun routineDone(task: Task, day: LocalDate) = RewardEvent(
        cause = RewardCause.ROUTINE_DONE,
        refId = task.id,
        day = day,
        xp = ROUTINE_XP,
        coins = ROUTINE_COINS,
    )

    /**
     * Lo ganado por los bloques de enfoque de [session] **ya terminados** a la hora [now]; un bloque a medias no
     * cuenta, pero tampoco quita lo anterior. Es el día del último bloque terminado, no el de hoy, para que una
     * sesión cerrada tarde sume al día en que se trabajó. Nulo si no se terminó ningún bloque.
     */
    fun focusSession(session: TimerSession, now: Instant, zone: ZoneId): RewardEvent? {
        var cursor = session.startedAt
        var xp = 0
        var minutes = 0
        var blocks = 0
        var lastBlockEnd: Instant? = null
        for (phase in TimerPlan.phases(session.mode)) {
            val end = cursor + phase.duration
            if (end > now) break
            if (phase.kind == TimerPhaseKind.FOCUS && phase.minutes >= FOCUS_MIN_BLOCK_MINUTES) {
                xp += max(1, phase.minutes / FOCUS_CHUNK_MINUTES) * FOCUS_XP_PER_CHUNK
                minutes += phase.minutes
                blocks++
                lastBlockEnd = end
            }
            cursor = end
        }
        if (blocks == 0 || lastBlockEnd == null) return null
        return RewardEvent(
            cause = RewardCause.FOCUS_SESSION,
            refId = session.startedAt.toEpochMilli(),
            day = lastBlockEnd.atZone(zone).toLocalDate(),
            xp = xp,
            coins = blocks * FOCUS_COINS_PER_BLOCK,
            minutes = minutes,
        )
    }

    /**
     * El límite de [packageName] se respetó el día [day]. La app se identifica por el código de su nombre de paquete:
     * basta para distinguir las pocas apps con límite de un usuario.
     */
    fun limitRespected(packageName: String, day: LocalDate) = RewardEvent(
        cause = RewardCause.LIMIT_RESPECTED,
        refId = packageName.hashCode().toLong(),
        day = day,
        xp = LIMIT_RESPECTED_XP,
        coins = 0,
    )

    fun stepDone(stepId: Long, day: LocalDate) = RewardEvent(
        cause = RewardCause.STEP_DONE,
        refId = stepId,
        day = day,
        xp = STEP_XP,
        coins = 0,
    )

    fun launchContinued(session: FocusLaunchSession, day: LocalDate) = RewardEvent(
        cause = RewardCause.LAUNCH_CONTINUED,
        refId = session.startedAt.toEpochMilli(),
        day = day,
        xp = LAUNCH_XP,
        coins = 0,
    )
}

/** Totales a partir de lo ganado cada día, con los topes diarios aplicados. */
object RewardTotals {

    fun xp(days: List<DayReward>): Int = days.sumOf { capped(it.xp, RewardRules.DAILY_XP_CAP) }

    fun coins(days: List<DayReward>): Int = days.sumOf { capped(it.coins, RewardRules.DAILY_COIN_CAP) }

    fun xpOn(days: List<DayReward>, day: LocalDate): Int =
        days.firstOrNull { it.day == day }?.let { capped(it.xp, RewardRules.DAILY_XP_CAP) } ?: 0

    fun coinsOn(days: List<DayReward>, day: LocalDate): Int =
        days.firstOrNull { it.day == day }?.let { capped(it.coins, RewardRules.DAILY_COIN_CAP) } ?: 0

    /** Minutos de enfoque del día. No tienen tope: son tiempo real. */
    fun minutesOn(days: List<DayReward>, day: LocalDate): Int =
        days.firstOrNull { it.day == day }?.minutes?.coerceAtLeast(0) ?: 0

    private fun capped(value: Int, cap: Int) = min(max(value, 0), cap)
}
