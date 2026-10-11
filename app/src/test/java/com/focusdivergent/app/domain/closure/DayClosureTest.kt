package com.focusdivergent.app.domain.closure

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DayClosureTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private val config = DayClosureConfig() // activo, de 21:30 a 06:00

    private fun at(text: String): Instant = Instant.parse("2026-10-10T${text}:00Z")
    private fun time(h: Int, m: Int = 0) = LocalTime.of(h, m)

    // ---- La noche del cierre

    @Test
    fun `la noche esta abierta desde la hora de cierre hasta la de la mañana`() {
        val expected = mapOf(
            "12:00" to false, "21:29" to false, "21:30" to true, "23:59" to true,
            "00:00" to true, "03:15" to true, "05:59" to true, "06:00" to false, "06:01" to false,
        )
        for ((hour, open) in expected) assertEquals(hour, open, ClosureWindow.isOpen(config, at(hour), utc))
    }

    @Test
    fun `con el cierre desactivado la noche nunca esta abierta`() {
        val off = config.copy(enabled = false)

        for (hour in listOf("21:30", "23:00", "02:00")) assertFalse(hour, ClosureWindow.isOpen(off, at(hour), utc))
    }

    @Test
    fun `respeta las horas elegidas`() {
        val custom = config.copy(closeAt = time(22, 30), liftUntil = time(7))

        assertFalse(ClosureWindow.isOpen(custom, at("22:29"), utc))
        assertTrue(ClosureWindow.isOpen(custom, at("22:30"), utc))
        assertTrue(ClosureWindow.isOpen(custom, at("06:59"), utc))
        assertFalse(ClosureWindow.isOpen(custom, at("07:00"), utc))
    }

    @Test
    fun `la hora se mira en la zona del movil, no en UTC`() {
        val madrid = ZoneId.of("Europe/Madrid") // UTC+2 en octubre

        assertFalse(ClosureWindow.isOpen(config, at("19:29"), madrid)) // 21:29 allí
        assertTrue(ClosureWindow.isOpen(config, at("19:30"), madrid)) // 21:30 allí
        assertTrue(ClosureWindow.isOpen(config, at("03:59"), madrid)) // 05:59 allí
        assertFalse(ClosureWindow.isOpen(config, at("04:00"), madrid)) // 06:00 allí
    }

    // ---- Qué día toca cerrar

    @Test
    fun `tras la hora de cierre toca cerrar hoy`() {
        assertEquals(LocalDate.of(2026, 10, 10), ClosureWindow.dueDay(config, at("21:30"), utc))
        assertEquals(LocalDate.of(2026, 10, 10), ClosureWindow.dueDay(config, at("23:59"), utc))
    }

    @Test
    fun `en la madrugada toca cerrar el dia anterior, por si no llego a cerrarse`() {
        assertEquals(LocalDate.of(2026, 10, 9), ClosureWindow.dueDay(config, at("00:30"), utc))
        assertEquals(LocalDate.of(2026, 10, 9), ClosureWindow.dueDay(config, at("05:59"), utc))
    }

    @Test
    fun `de dia no toca cerrar nada`() {
        for (hour in listOf("06:00", "12:00", "21:29")) assertNull(hour, ClosureWindow.dueDay(config, at(hour), utc))
    }

    @Test
    fun `con el cierre desactivado nunca toca cerrar`() {
        assertNull(ClosureWindow.dueDay(config.copy(enabled = false), at("22:00"), utc))
    }

    // ---- Estado con cambio pendiente

    @Test
    fun `un cambio pendiente solo rige desde su hora`() {
        val off = DayClosureConfig(enabled = false)
        val midnight = Instant.parse("2026-10-11T00:00:00Z")
        val state = DayClosureState(effective = off, pending = PendingClosureConfig(config, midnight))

        assertEquals(off, state.configAt(Instant.parse("2026-10-10T23:59:59Z")))
        assertEquals(config, state.configAt(midnight))
        assertEquals(config, state.configAt(Instant.parse("2026-10-12T12:00:00Z")))
    }

    @Test
    fun `sin cambio pendiente rige lo de siempre`() {
        assertEquals(config, DayClosureState(effective = config).configAt(at("12:00")))
    }

    // ---- Regla de los cambios: endurecer ya, relajar a medianoche

    private val midnight = Instant.parse("2026-10-11T00:00:00Z")

    @Test
    fun `activar el cierre, cerrar antes y alargar la noche relajan`() {
        assertTrue(ClosurePolicy.isRelaxation(config.copy(enabled = false), config))
        assertTrue(ClosurePolicy.isRelaxation(config, config.copy(closeAt = time(21))))
        assertTrue(ClosurePolicy.isRelaxation(config, config.copy(liftUntil = time(7))))
    }

    @Test
    fun `desactivar el cierre, cerrar mas tarde y acortar la noche endurecen`() {
        assertFalse(ClosurePolicy.isRelaxation(config, config.copy(enabled = false)))
        assertFalse(ClosurePolicy.isRelaxation(config, config.copy(closeAt = time(22))))
        assertFalse(ClosurePolicy.isRelaxation(config, config.copy(liftUntil = time(5))))
        assertFalse(ClosurePolicy.isRelaxation(config, config))
    }

    @Test
    fun `lo que endurece rige al momento y no deja nada pendiente`() {
        val state = ClosurePolicy.plan(config, config.copy(closeAt = time(22, 30), liftUntil = time(5)), midnight)

        assertEquals(DayClosureState(effective = config.copy(closeAt = time(22, 30), liftUntil = time(5))), state)
    }

    @Test
    fun `lo que relaja espera a medianoche y mientras tanto rige lo de antes`() {
        val state = ClosurePolicy.plan(config.copy(enabled = false), config, midnight)

        assertEquals(config.copy(enabled = false), state.effective)
        assertEquals(PendingClosureConfig(config, midnight), state.pending)
    }

    @Test
    fun `un cambio mixto aplica ya la parte que endurece y deja la otra para medianoche`() {
        // Cierra más tarde (endurece) pero alarga la noche (relaja).
        val desired = config.copy(closeAt = time(22), liftUntil = time(7))

        val state = ClosurePolicy.plan(config, desired, midnight)

        assertEquals(config.copy(closeAt = time(22)), state.effective)
        assertEquals(PendingClosureConfig(desired, midnight), state.pending)
    }

    @Test
    fun `pedir lo que ya rige no deja nada pendiente`() {
        assertEquals(DayClosureState(effective = config), ClosurePolicy.plan(config, config, midnight))
    }

    @Test
    fun `las opciones que ofrece la pantalla incluyen los valores de serie`() {
        assertTrue(DayClosureConfig.DEFAULT_CLOSE_AT in ClosureOptions.CLOSE_TIMES)
        assertTrue(DayClosureConfig.DEFAULT_LIFT_UNTIL in ClosureOptions.LIFT_TIMES)
    }
}
