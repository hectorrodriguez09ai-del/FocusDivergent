package com.focusdivergent.app.data.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageEventsCalculatorTest {

    private val a = "com.example.a"
    private val b = "com.example.b"

    private fun resume(pkg: String, at: Long, cls: String = "Main") = RawUsageEvent(pkg, cls, isResume = true, timestampMillis = at)
    private fun pause(pkg: String, at: Long, cls: String = "Main") = RawUsageEvent(pkg, cls, isResume = false, timestampMillis = at)

    @Test
    fun `sin eventos no hay uso ni app en primer plano`() {
        val snapshot = computeUsageSnapshot(emptyList(), windowStartMillis = 0, nowMillis = 10_000)

        assertNull(snapshot.foregroundPackage)
        assertEquals(emptyMap<String, Long>(), snapshot.usedMillisByPackage)
    }

    @Test
    fun `suma el tramo entre reanudar y pausar`() {
        val snapshot = computeUsageSnapshot(listOf(resume(a, 1_000), pause(a, 4_000)), 0, 10_000)

        assertEquals(mapOf(a to 3_000L), snapshot.usedMillisByPackage)
        assertNull(snapshot.foregroundPackage)
    }

    @Test
    fun `suma varias sesiones de la misma app`() {
        val events = listOf(resume(a, 1_000), pause(a, 2_000), resume(a, 5_000), pause(a, 8_000))

        assertEquals(mapOf(a to 4_000L), computeUsageSnapshot(events, 0, 10_000).usedMillisByPackage)
    }

    @Test
    fun `una sesion abierta cuenta hasta ahora y marca la app en primer plano`() {
        val snapshot = computeUsageSnapshot(listOf(resume(a, 2_000)), 0, 10_000)

        assertEquals(mapOf(a to 8_000L), snapshot.usedMillisByPackage)
        assertEquals(a, snapshot.foregroundPackage)
    }

    @Test
    fun `al cambiar de app la ultima en abrirse es la de primer plano`() {
        val events = listOf(resume(a, 1_000), pause(a, 3_000), resume(b, 3_000))

        val snapshot = computeUsageSnapshot(events, 0, 10_000)

        assertEquals(mapOf(a to 2_000L, b to 7_000L), snapshot.usedMillisByPackage)
        assertEquals(b, snapshot.foregroundPackage)
    }

    @Test
    fun `una pausa sin reanudacion cuenta desde el inicio de la ventana`() {
        // La app ya estaba abierta a medianoche y se cerró a las 00:00:05.
        val snapshot = computeUsageSnapshot(listOf(pause(a, 5_000)), windowStartMillis = 0, nowMillis = 10_000)

        assertEquals(mapOf(a to 5_000L), snapshot.usedMillisByPackage)
    }

    @Test
    fun `una segunda reanudacion sin pausa no duplica el tiempo`() {
        val events = listOf(resume(a, 1_000), resume(a, 3_000), pause(a, 6_000))

        assertEquals(mapOf(a to 5_000L), computeUsageSnapshot(events, 0, 10_000).usedMillisByPackage)
    }

    @Test
    fun `actividades distintas de la misma app suman al mismo paquete`() {
        val events = listOf(
            resume(a, 1_000, cls = "Home"), pause(a, 2_000, cls = "Home"),
            resume(a, 2_000, cls = "Detail"), pause(a, 5_000, cls = "Detail"),
        )

        assertEquals(mapOf(a to 4_000L), computeUsageSnapshot(events, 0, 10_000).usedMillisByPackage)
    }

    @Test
    fun `los eventos desordenados se procesan por fecha`() {
        val events = listOf(pause(a, 4_000), resume(a, 1_000))

        assertEquals(mapOf(a to 3_000L), computeUsageSnapshot(events, 0, 10_000).usedMillisByPackage)
    }
}
