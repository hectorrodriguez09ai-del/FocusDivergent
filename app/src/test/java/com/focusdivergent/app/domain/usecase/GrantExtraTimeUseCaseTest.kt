package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.model.AppLimit
import com.focusdivergent.app.domain.model.UsageSnapshot
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GrantExtraTimeUseCaseTest {

    private val social = "com.example.social"

    private val minute = 60_000L

    private class Setup(
        limit: AppLimit?,
        existing: List<com.focusdivergent.app.domain.model.TimeExtension> = emptyList(),
        yieldOnRead: Boolean = false,
        usedMillis: Long = 0,
    ) {
        val extensions = FakeTimeExtensions(existing, yieldOnRead)
        val useCase = GrantExtraTimeUseCase(
            limits = FakeAppLimits(listOfNotNull(limit)),
            extensions = extensions,
            usage = FakeUsage(UsageSnapshot("com.example.social", mapOf("com.example.social" to usedMillis))),
            clock = TEST_CLOCK,
        )
    }

    @Test
    fun `concede quince minutos con la hora de ahora y cuenta las que quedan`() = runTest {
        val s = Setup(AppLimit(social, 10))

        val result = s.useCase(social)

        assertEquals(GrantResult.Granted(extensionsLeft = 2), result)
        assertEquals(15, s.extensions.items.single().minutes)
        assertEquals(TEST_NOW, s.extensions.items.single().grantedAt)
    }

    @Test
    fun `el tiempo pasado mientras se veia la pausa no se descuenta de los 15 minutos`() = runTest {
        // Límite de 10 min y 30 usados (20 de más, con la app abierta bajo la pantalla de pausa).
        val s = Setup(AppLimit(social, 10), usedMillis = 30 * minute)

        s.useCase(social)

        // 15 ganados + 20 ya consumidos de más: el límite pasa a 45 y a la app le quedan 15 min reales.
        assertEquals(35, s.extensions.items.single().minutes)
    }

    @Test
    fun `una fraccion de minuto pasada del limite se redondea hacia arriba`() = runTest {
        val s = Setup(AppLimit(social, 10), usedMillis = 10 * minute + 1_000)

        s.useCase(social)

        assertEquals(16, s.extensions.items.single().minutes)
    }

    @Test
    fun `la segunda ampliacion tiene en cuenta la primera al calcular lo pasado de limite`() = runTest {
        // 10 + 15 de la primera = 25 permitidos; 31 usados => 6 de más.
        val s = Setup(AppLimit(social, 10), listOf(extension(social, minutes = 15)), usedMillis = 31 * minute)

        s.useCase(social)

        assertEquals(21, s.extensions.items.last().minutes)
    }

    @Test
    fun `concede exactamente tres y a la cuarta dice que no quedan`() = runTest {
        val s = Setup(AppLimit(social, 10))

        assertEquals(GrantResult.Granted(2), s.useCase(social))
        assertEquals(GrantResult.Granted(1), s.useCase(social))
        assertEquals(GrantResult.Granted(0), s.useCase(social))
        assertEquals(GrantResult.NoMoreExtensions, s.useCase(social))
        assertEquals(3, s.extensions.items.size)
    }

    @Test
    fun `una app con bloqueo estricto nunca recibe tiempo`() = runTest {
        val s = Setup(AppLimit(social, 10, isStrictlyBlocked = true))

        assertEquals(GrantResult.StrictlyBlocked, s.useCase(social))
        assertEquals(0, s.extensions.items.size)
    }

    @Test
    fun `un limite firme nunca concede ampliaciones`() = runTest {
        val s = Setup(AppLimit(social, 10, extensionsPerDay = 0))

        assertEquals(GrantResult.NoMoreExtensions, s.useCase(social))
        assertEquals(0, s.extensions.items.size)
    }

    @Test
    fun `el tope de ampliaciones al dia lo marca el limite`() = runTest {
        val s = Setup(AppLimit(social, 10, extensionsPerDay = 1))

        assertEquals(GrantResult.Granted(0), s.useCase(social))
        assertEquals(GrantResult.NoMoreExtensions, s.useCase(social))
        assertEquals(1, s.extensions.items.size)
    }

    @Test
    fun `una app sin limite no recibe tiempo`() = runTest {
        val s = Setup(limit = null)

        assertEquals(GrantResult.NoLimit, s.useCase(social))
        assertEquals(0, s.extensions.items.size)
    }

    @Test
    fun `las ampliaciones de ayer no gastan el tope de hoy`() = runTest {
        val yesterday = listOf(extension(social, hoursAgo = 13), extension(social, hoursAgo = 14), extension(social, hoursAgo = 15))
        val s = Setup(AppLimit(social, 10), yesterday)

        assertEquals(GrantResult.Granted(2), s.useCase(social))
    }

    @Test
    fun `las ampliaciones de otra app no gastan el tope`() = runTest {
        val others = List(3) { extension("com.example.video", hoursAgo = 1) }
        val s = Setup(AppLimit(social, 10), others)

        assertEquals(GrantResult.Granted(2), s.useCase(social))
    }

    @Test
    fun `cinco peticiones a la vez solo conceden tres`() = runTest {
        // Con yieldOnRead las cinco leen el contador antes de que ninguna escriba: sin el Mutex pasarían todas.
        val s = Setup(AppLimit(social, 10), yieldOnRead = true)

        val results = List(5) { async { s.useCase(social) } }.awaitAll()

        assertEquals(3, results.count { it is GrantResult.Granted })
        assertEquals(2, results.count { it == GrantResult.NoMoreExtensions })
        assertEquals(3, s.extensions.items.size)
    }
}
