package com.focusdivergent.app.presentation.block

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ObligationReminderContentTest {

    private val start = Instant.parse("2026-10-10T12:10:00Z")

    @Test
    fun `a diez minutos exactos muestra 10`() {
        assertEquals(10, minutesUntil(start, now = Instant.parse("2026-10-10T12:00:00Z")))
    }

    @Test
    fun `redondea hacia arriba para no decir 0 cuando aun quedan segundos`() {
        assertEquals(10, minutesUntil(start, now = Instant.parse("2026-10-10T12:00:01Z")))
        assertEquals(1, minutesUntil(start, now = Instant.parse("2026-10-10T12:09:01Z")))
        assertEquals(1, minutesUntil(start, now = Instant.parse("2026-10-10T12:09:59Z")))
    }

    @Test
    fun `nunca baja de un minuto mientras el aviso sigue en pantalla`() {
        assertEquals(1, minutesUntil(start, now = start))
        assertEquals(1, minutesUntil(start, now = start.plusSeconds(30)))
    }
}
