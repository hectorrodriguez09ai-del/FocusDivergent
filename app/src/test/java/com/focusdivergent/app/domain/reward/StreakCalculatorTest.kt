package com.focusdivergent.app.domain.reward

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La racha de Rutinas. Hoy es sábado 10 de octubre de 2026, así que las semanas (lunes a domingo) son:
 * 28 sep - 4 oct, 5 oct - 11 oct.
 */
class StreakCalculatorTest {

    private val today = LocalDate.of(2026, 10, 10)

    private fun oct(day: Int) = LocalDate.of(2026, 10, day)
    private fun sep(day: Int) = LocalDate.of(2026, 9, day)

    private fun streak(vararg days: LocalDate) = StreakCalculator.compute(days.toList(), today)

    @Test
    fun `sin ninguna rutina hecha no hay racha`() {
        assertEquals(StreakInfo(0, 0), streak())
    }

    @Test
    fun `una rutina hoy empieza la racha`() {
        assertEquals(StreakInfo(1, 1), streak(oct(10)))
    }

    @Test
    fun `dias seguidos hasta hoy suman uno por dia`() {
        assertEquals(StreakInfo(3, 3), streak(oct(8), oct(9), oct(10)))
    }

    @Test
    fun `hoy sin hacer todavia no rompe la racha, porque el dia no ha terminado`() {
        assertEquals(StreakInfo(3, 3), streak(oct(7), oct(8), oct(9)))
    }

    @Test
    fun `un dia sin ninguna rutina se perdona una vez por semana y la racha sigue`() {
        // Lun, mar, (miércoles sin nada), jue, vie, sáb.
        assertEquals(StreakInfo(5, 5), streak(oct(5), oct(6), oct(8), oct(9), oct(10)))
    }

    @Test
    fun `el dia de gracia no suma y la racha cuenta solo los dias con rutina`() {
        // Mié y vie con rutina, jueves perdonado: son 2, no 3.
        assertEquals(StreakInfo(2, 2), streak(oct(7), oct(9)))
    }

    @Test
    fun `dos dias sin nada en la misma semana rompen la racha, y la mejor se conserva`() {
        // Lun, mar, mié; jue (gracia); vie sin nada: se rompe; sáb empieza otra.
        assertEquals(StreakInfo(current = 1, best = 3), streak(oct(5), oct(6), oct(7), oct(10)))
    }

    @Test
    fun `la gracia se renueva cada semana`() {
        // 28 sep a 10 oct con un hueco en cada semana (30 sep y 6 oct): ninguno rompe.
        val days = listOf(sep(28), sep(29), oct(1), oct(2), oct(3), oct(4), oct(5), oct(7), oct(8), oct(9), oct(10))
        assertEquals(StreakInfo(11, 11), StreakCalculator.compute(days, today))
    }

    @Test
    fun `una racha rota empieza de cero y la mejor no baja`() {
        // Cinco días a principios de septiembre y nada hasta hoy.
        val days = listOf(sep(1), sep(2), sep(3), sep(4), sep(5), oct(10))
        assertEquals(StreakInfo(current = 1, best = 5), StreakCalculator.compute(days, today))
    }

    @Test
    fun `un dia vacio cuando no habia racha que proteger no gasta la gracia de la semana`() {
        // 1 sep y luego nada hasta el miércoles 7 oct. Lunes y martes vacíos no gastan la gracia de esa semana,
        // así que el jueves vacío todavía se perdona: 7, (8), 9 y 10 son 3 días.
        val days = listOf(sep(1), oct(7), oct(9), oct(10))
        assertEquals(StreakInfo(current = 3, best = 3), StreakCalculator.compute(days, today))
    }

    @Test
    fun `los dias posteriores a hoy no cuentan`() {
        assertEquals(StreakInfo(1, 1), streak(oct(10), oct(11), oct(12)))
    }

    @Test
    fun `varias rutinas el mismo dia cuentan una sola vez`() {
        assertEquals(StreakInfo(2, 2), streak(oct(9), oct(9), oct(10), oct(10)))
    }

    @Test
    fun `la mejor racha nunca es menor que la actual`() {
        for (n in 0..40) {
            val days = (0 until n).map { today.minusDays(it.toLong() * 2 % 7 + it / 3L) }
            val result = StreakCalculator.compute(days, today)
            assertTrue("mejor ${result.best} < actual ${result.current} con $days", result.best >= result.current)
        }
    }
}
