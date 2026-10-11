package com.focusdivergent.app.domain.policy

import com.focusdivergent.app.domain.model.AppLimit
import org.junit.Assert.assertEquals
import org.junit.Test

class ExtensionPolicyTest {

    private val limit = AppLimit("com.example.social", dailyMinutesAllowed = 30)

    @Test
    fun `el tope son tres ampliaciones de quince minutos, 45 en total`() {
        assertEquals(3, AppLimit.DEFAULT_EXTENSIONS_PER_DAY)
        assertEquals(15, ExtensionPolicy.MINUTES_PER_EXTENSION)
        assertEquals(45, AppLimit.DEFAULT_EXTENSIONS_PER_DAY * ExtensionPolicy.MINUTES_PER_EXTENSION)
        // Un límite nuevo es flexible: trae el tope completo.
        assertEquals(3, limit.extensionsPerDay)
    }

    @Test
    fun `un limite firme no tiene ampliaciones aunque no se haya usado ninguna`() {
        val firm = limit.copy(extensionsPerDay = 0)

        assertEquals(0, ExtensionPolicy.remaining(firm, usedToday = 0))
    }

    @Test
    fun `el tope lo marca el limite, no una constante global`() {
        val two = limit.copy(extensionsPerDay = 2)

        assertEquals(2, ExtensionPolicy.remaining(two, usedToday = 0))
        assertEquals(1, ExtensionPolicy.remaining(two, usedToday = 1))
        assertEquals(0, ExtensionPolicy.remaining(two, usedToday = 2))
        assertEquals(0, ExtensionPolicy.remaining(two, usedToday = 3))
    }

    @Test
    fun `quedan las que faltan hasta el tope`() {
        assertEquals(3, ExtensionPolicy.remaining(limit, usedToday = 0))
        assertEquals(2, ExtensionPolicy.remaining(limit, usedToday = 1))
        assertEquals(1, ExtensionPolicy.remaining(limit, usedToday = 2))
        assertEquals(0, ExtensionPolicy.remaining(limit, usedToday = 3))
    }

    @Test
    fun `nunca sale un numero negativo aunque haya mas ampliaciones que el tope`() {
        assertEquals(0, ExtensionPolicy.remaining(limit, usedToday = 7))
    }

    @Test
    fun `sin haberse pasado del limite se conceden exactamente quince minutos`() {
        assertEquals(15, ExtensionPolicy.minutesToGrant(limit, extraMinutesToday = 0, usedMillis = 0))
        assertEquals(15, ExtensionPolicy.minutesToGrant(limit, extraMinutesToday = 0, usedMillis = 30 * 60_000L))
    }

    @Test
    fun `lo pasado del limite se suma a los quince minutos, redondeado hacia arriba`() {
        assertEquals(15 + 5, ExtensionPolicy.minutesToGrant(limit, 0, usedMillis = 35 * 60_000L))
        assertEquals(15 + 6, ExtensionPolicy.minutesToGrant(limit, 0, usedMillis = 35 * 60_000L + 1))
    }

    @Test
    fun `el usuario siempre recibe quince minutos de uso reales tras la ampliacion`() {
        // Esperó 7 minutos pasado el límite antes de resolver el reto.
        val used = (limit.dailyMinutesAllowed + 7) * 60_000L
        val extra = ExtensionPolicy.minutesToGrant(limit, 0, used)

        // Con el nuevo límite aún puede usar 14 minutos más, pero no 15: son exactamente quince desde ahora.
        assertEquals(null, BlockPolicy.evaluate(limit, used + 14 * 60_000L, extraMinutes = extra))
        assertEquals(true, BlockPolicy.evaluate(limit, used + 15 * 60_000L, extraMinutes = extra) != null)
    }

    @Test
    fun `una app con bloqueo estricto no tiene ampliaciones`() {
        assertEquals(0, ExtensionPolicy.remaining(limit.copy(isStrictlyBlocked = true), usedToday = 0))
    }
}
