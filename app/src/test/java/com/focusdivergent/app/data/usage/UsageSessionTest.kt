package com.focusdivergent.app.data.usage

import org.junit.Assert.assertEquals
import org.junit.Test

/** Sesión continua en la app en primer plano (base del Reality Check). Los tiempos van en segundos para leerse bien. */
class UsageSessionTest {

    private val a = "com.example.a"
    private val b = "com.example.b"
    private val s = 1_000L

    private fun resume(pkg: String, sec: Long, cls: String = "Main") = RawUsageEvent(pkg, cls, true, sec * s)
    private fun pause(pkg: String, sec: Long, cls: String = "Main") = RawUsageEvent(pkg, cls, false, sec * s)

    private fun session(events: List<RawUsageEvent>, nowSec: Long) =
        computeUsageSnapshot(events, windowStartMillis = 0, nowMillis = nowSec * s).foregroundSessionMillis / s

    @Test
    fun `sin app en primer plano la sesion es cero`() {
        assertEquals(0L, session(emptyList(), nowSec = 100))
        assertEquals(0L, session(listOf(resume(a, 10), pause(a, 50)), nowSec = 100))
    }

    @Test
    fun `una app abierta sin interrupciones lleva desde que se abrio`() {
        assertEquals(90L, session(listOf(resume(a, 10)), nowSec = 100))
    }

    @Test
    fun `una salida corta a otra app no corta la sesion`() {
        // A de 0 a 600, B de 600 a 640 (40 s), A de nuevo desde 640: la sesión de A sigue contando desde 0.
        val events = listOf(resume(a, 0), pause(a, 600), resume(b, 600), pause(b, 640), resume(a, 640))

        assertEquals(1_000L, session(events, nowSec = 1_000))
    }

    @Test
    fun `una salida de justo un minuto todavia se tolera`() {
        val events = listOf(resume(a, 0), pause(a, 600), resume(a, 660))

        assertEquals(1_000L, session(events, nowSec = 1_000))
    }

    @Test
    fun `una salida de mas de un minuto reinicia la sesion`() {
        val events = listOf(resume(a, 0), pause(a, 600), resume(a, 661))

        assertEquals(339L, session(events, nowSec = 1_000))
    }

    @Test
    fun `apagar y encender la pantalla en menos de un minuto no reinicia la sesion`() {
        // Apagar la pantalla pausa la actividad; al encenderla se reanuda.
        val events = listOf(resume(a, 0), pause(a, 900), resume(a, 930))

        assertEquals(1_200L, session(events, nowSec = 1_200))
    }

    @Test
    fun `varias salidas cortas seguidas encadenan la sesion`() {
        val events = listOf(
            resume(a, 0), pause(a, 100), resume(a, 150), pause(a, 300), resume(a, 340), pause(a, 500), resume(a, 560),
        )

        assertEquals(700L, session(events, nowSec = 700))
    }

    @Test
    fun `una salida larga en medio de la jornada solo deja contar la ultima racha`() {
        val events = listOf(
            resume(a, 0), pause(a, 1_000), // racha de la mañana
            resume(b, 1_000), pause(b, 5_000),
            resume(a, 5_000), pause(a, 5_100), resume(a, 5_130), // racha actual con una pausa corta
        )

        // La racha actual empieza en 5000 (la pausa de 30 s se tolera); la de la mañana queda fuera.
        assertEquals(500L, session(events, nowSec = 5_500))
    }

    @Test
    fun `pasar de una actividad a otra de la misma app es continuo`() {
        val events = listOf(
            resume(a, 0, cls = "Home"), pause(a, 300, cls = "Home"), resume(a, 300, cls = "Detail"),
        )

        assertEquals(500L, session(events, nowSec = 500))
    }

    @Test
    fun `la sesion es la de la app en primer plano, no la de otra`() {
        // A lleva mucho rato, pero ahora se está en B desde hace 30 s.
        val events = listOf(resume(a, 0), pause(a, 1_000), resume(b, 1_000))

        assertEquals(30L, session(events, nowSec = 1_030))
    }

    @Test
    fun `una app abierta desde antes de la ventana cuenta desde el inicio de la ventana`() {
        // Pausada sin reanudación (abierta a medianoche), vuelve a abrirse 30 s después.
        val events = listOf(pause(a, 100), resume(a, 130))

        assertEquals(400L, session(events, nowSec = 400))
    }
}
