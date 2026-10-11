package com.focusdivergent.app.presentation.block

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class CountdownTest {

    private val start = Instant.parse("2026-10-10T12:00:00Z")
    private val end = start.plusSeconds(120)

    @Test
    fun `al empezar faltan 120 segundos y se lee 2 00`() {
        assertEquals(120L, remainingSeconds(end, start))
        assertEquals("2:00", countdownText(120))
    }

    @Test
    fun `los segundos se redondean hacia arriba para no enseñar 0 00 antes de tiempo`() {
        assertEquals(120L, remainingSeconds(end, start.plusMillis(1)))
        assertEquals(1L, remainingSeconds(end, end.minusMillis(1)))
        assertEquals(1L, remainingSeconds(end, end.minusMillis(999)))
        assertEquals(2L, remainingSeconds(end, end.minusMillis(1_001)))
    }

    @Test
    fun `al llegar a la hora, o pasada, queda cero y nunca un numero negativo`() {
        assertEquals(0L, remainingSeconds(end, end))
        assertEquals(0L, remainingSeconds(end, end.plusSeconds(500)))
        assertEquals("0:00", countdownText(0))
        assertEquals("0:00", countdownText(-5))
    }

    @Test
    fun `el formato lleva siempre dos cifras de segundos`() {
        assertEquals("1:45", countdownText(105))
        assertEquals("0:07", countdownText(7))
        assertEquals("1:00", countdownText(60))
        assertEquals("0:59", countdownText(59))
        assertEquals("10:05", countdownText(605))
    }
}
