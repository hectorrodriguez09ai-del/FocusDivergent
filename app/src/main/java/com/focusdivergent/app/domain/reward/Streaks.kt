package com.focusdivergent.app.domain.reward

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.max

/**
 * @property current días seguidos con al menos una Rutina hecha, contando hoy si ya se hizo una.
 * @property best la mejor racha que ha habido; nunca es menor que [current].
 */
data class StreakInfo(
    val current: Int = 0,
    val best: Int = 0,
)

/**
 * La racha de Rutinas, pensada para no castigar el día malo (SRD §3.2, RNF-3):
 *
 * - Cuenta un día si se hizo **al menos una** Rutina, no todas.
 * - **Un día de gracia por semana** (de lunes a domingo): si ese día no se hizo ninguna, la racha sigue como estaba.
 *   No suma, pero tampoco se rompe, y no avisa de nada.
 * - Hoy nunca rompe la racha: el día aún no ha terminado.
 * - Si se rompe, no hay mensaje: el contador empieza de nuevo y la mejor racha se conserva.
 */
object StreakCalculator {

    fun compute(routineDays: Collection<LocalDate>, today: LocalDate): StreakInfo {
        val days = routineDays.filter { it <= today }.toSortedSet()
        if (days.isEmpty()) return StreakInfo()

        var streak = 0
        var best = 0
        val weeksWithGrace = mutableSetOf<LocalDate>()

        var day = days.first()
        while (day < today) {
            if (day in days) {
                streak++
                best = max(best, streak)
            } else if (streak > 0) {
                // Un hueco: se perdona si esa semana no se había usado ya la gracia; si no, se empieza de cero.
                val week = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                if (!weeksWithGrace.add(week)) streak = 0
            }
            day = day.plusDays(1)
        }
        if (today in days) {
            streak++
            best = max(best, streak)
        }
        return StreakInfo(current = streak, best = best)
    }
}
