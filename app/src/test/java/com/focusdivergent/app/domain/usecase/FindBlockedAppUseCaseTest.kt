package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.model.AppLimit
import com.focusdivergent.app.domain.model.BlockReason
import com.focusdivergent.app.domain.model.BlockedApp
import com.focusdivergent.app.domain.model.TimeExtension
import com.focusdivergent.app.domain.model.UsageSnapshot
import com.focusdivergent.app.domain.closure.DayClosureConfig
import com.focusdivergent.app.domain.closure.DayClosureState
import com.focusdivergent.app.domain.closure.PendingClosureConfig
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FindBlockedAppUseCaseTest {

    private val own = "com.focusdivergent.app"
    private val social = "com.example.social"
    private val minute = 60_000L

    private fun useCase(
        limits: List<AppLimit> = emptyList(),
        extensions: List<TimeExtension> = emptyList(),
        closure: FakeDayClosureSettings = FakeDayClosureSettings(),
        clock: Clock = TEST_CLOCK,
    ) = FindBlockedAppUseCase(FakeAppLimits(limits), FakeTimeExtensions(extensions), closure, clock, own)

    /** Un reloj fijo a una hora de [TEST_TODAY] (UTC). */
    private fun at(time: String): Clock = Clock.fixed(Instant.parse("2026-10-10T${time}:00Z"), ZoneOffset.UTC)

    private fun snapshot(foreground: String?, vararg used: Pair<String, Long>) =
        UsageSnapshot(foreground, mapOf(*used))

    @Test
    fun `sin app en primer plano no hay nada que bloquear`() = runTest {
        val result = useCase(listOf(AppLimit(social, 10)))(snapshot(null, social to 99 * minute))

        assertNull(result)
    }

    @Test
    fun `una app sin limite nunca se bloquea`() = runTest {
        val result = useCase()(snapshot("com.example.other", "com.example.other" to 600 * minute))

        assertNull(result)
    }

    @Test
    fun `por debajo del limite no se bloquea`() = runTest {
        val result = useCase(listOf(AppLimit(social, 10)))(snapshot(social, social to 5 * minute))

        assertNull(result)
    }

    @Test
    fun `al agotar el limite se bloquea la app y quedan las tres ampliaciones`() = runTest {
        val result = useCase(listOf(AppLimit(social, 10)))(snapshot(social, social to 12 * minute))

        assertEquals(
            BlockedApp(
                social,
                BlockReason.LimitReached(usedMillis = 12 * minute, allowedMillis = 10 * minute),
                extensionsLeft = 3,
            ),
            result,
        )
    }

    @Test
    fun `una app con bloqueo estricto se bloquea sin ampliaciones`() = runTest {
        val result = useCase(listOf(AppLimit(social, 60, isStrictlyBlocked = true)))(snapshot(social))

        assertEquals(BlockedApp(social, BlockReason.StrictlyBlocked, extensionsLeft = 0, extensionsPerDay = 0), result)
    }

    @Test
    fun `un limite firme se bloquea sin ampliaciones y lo dice`() = runTest {
        val result = useCase(listOf(AppLimit(social, 10, extensionsPerDay = 0)))(snapshot(social, social to 12 * minute))

        assertEquals(0, (result as BlockedApp).extensionsLeft)
        assertEquals(0, result.extensionsPerDay)
        assertEquals(BlockReason.LimitReached(12 * minute, 10 * minute), result.reason)
    }

    @Test
    fun `un limite flexible informa de cuantas ampliaciones permite en total`() = runTest {
        val result = useCase(listOf(AppLimit(social, 10)), listOf(extension(social)))(snapshot(social, social to 30 * minute))

        assertEquals(3, (result as BlockedApp).extensionsPerDay)
        assertEquals(2, result.extensionsLeft)
    }

    @Test
    fun `solo cuenta el limite de la app en pantalla, no el de otras`() = runTest {
        val result = useCase(listOf(AppLimit(social, 10), AppLimit("com.example.video", 60)))(
            snapshot("com.example.video", social to 99 * minute, "com.example.video" to minute),
        )

        assertNull(result)
    }

    @Test
    fun `la propia app nunca se bloquea aunque tenga limite`() = runTest {
        val result = useCase(listOf(AppLimit(own, 1, isStrictlyBlocked = true)))(snapshot(own, own to 99 * minute))

        assertNull(result)
    }

    @Test
    fun `una ampliacion de hoy suma sus minutos al limite y desbloquea`() = runTest {
        // 10 min de límite + 15 de ampliación = 25; con 12 usados todavía hay tiempo.
        val result = useCase(listOf(AppLimit(social, 10)), listOf(extension(social)))(snapshot(social, social to 12 * minute))

        assertNull(result)
    }

    @Test
    fun `al agotar tambien la ampliacion vuelve a bloquear y quedan dos`() = runTest {
        val result = useCase(listOf(AppLimit(social, 10)), listOf(extension(social)))(snapshot(social, social to 26 * minute))

        assertEquals(
            BlockedApp(
                social,
                BlockReason.LimitReached(usedMillis = 26 * minute, allowedMillis = 25 * minute),
                extensionsLeft = 2,
            ),
            result,
        )
    }

    @Test
    fun `con las tres ampliaciones gastadas no queda ninguna y el limite suma 45 minutos`() = runTest {
        val spent = listOf(extension(social, 1), extension(social, 2), extension(social, 3))

        val result = useCase(listOf(AppLimit(social, 10)), spent)(snapshot(social, social to 60 * minute))

        assertEquals(
            BlockedApp(
                social,
                BlockReason.LimitReached(usedMillis = 60 * minute, allowedMillis = 55 * minute),
                extensionsLeft = 0,
            ),
            result,
        )
    }

    @Test
    fun `las ampliaciones de ayer y de otras apps no cuentan`() = runTest {
        val old = listOf(extension(social, hoursAgo = 13), extension("com.example.video", hoursAgo = 1))

        val result = useCase(listOf(AppLimit(social, 10)), old)(snapshot(social, social to 12 * minute))

        assertEquals(3, result?.extensionsLeft)
    }

    @Test
    fun `no consulta las ampliaciones si la app aun no ha agotado su limite`() = runTest {
        val extensions = FakeTimeExtensions()
        val useCase = FindBlockedAppUseCase(FakeAppLimits(listOf(AppLimit(social, 10))), extensions, FakeDayClosureSettings(), TEST_CLOCK, own)

        useCase(snapshot(social, social to 2 * minute))

        assertEquals(0, extensions.reads)
    }

    // ---- Cierre de Jornada: las apps de ocio quedan libres de noche

    private val leisure = AppLimit(social, 10, isLeisure = true)
    private val over = snapshot(social, social to 12 * minute)

    @Test
    fun `una app de ocio agotada se bloquea de dia`() = runTest {
        assertEquals(social, useCase(listOf(leisure), clock = at("12:00"))(over)?.packageName)
        assertEquals(social, useCase(listOf(leisure), clock = at("21:29"))(over)?.packageName)
        assertEquals(social, useCase(listOf(leisure), clock = at("06:00"))(over)?.packageName)
    }

    @Test
    fun `una app de ocio agotada queda libre desde la hora de cierre hasta la de la mañana`() = runTest {
        assertNull(useCase(listOf(leisure), clock = at("21:30"))(over))
        assertNull(useCase(listOf(leisure), clock = at("23:59"))(over))
        assertNull(useCase(listOf(leisure), clock = at("00:00"))(over))
        assertNull(useCase(listOf(leisure), clock = at("05:59"))(over))
    }

    @Test
    fun `una app que no es de ocio sigue bloqueada de noche`() = runTest {
        assertEquals(social, useCase(listOf(AppLimit(social, 10)), clock = at("23:00"))(over)?.packageName)
    }

    @Test
    fun `una app cerrada de ocio tambien queda libre de noche, porque asi lo eligio el usuario`() = runTest {
        val closed = AppLimit(social, 10, isStrictlyBlocked = true, extensionsPerDay = 0, isLeisure = true)

        assertNull(useCase(listOf(closed), clock = at("23:00"))(snapshot(social)))
        assertEquals(social, useCase(listOf(closed), clock = at("12:00"))(snapshot(social))?.packageName)
    }

    @Test
    fun `con el Cierre desactivado nada queda libre`() = runTest {
        val off = FakeDayClosureSettings(DayClosureConfig(enabled = false))

        assertEquals(social, useCase(listOf(leisure), closure = off, clock = at("23:00"))(over)?.packageName)
    }

    @Test
    fun `la hora de cierre y la de la mañana son las configuradas`() = runTest {
        val custom = FakeDayClosureSettings(DayClosureConfig(closeAt = LocalTime.of(22, 30), liftUntil = LocalTime.of(7, 0)))

        assertEquals(social, useCase(listOf(leisure), closure = custom, clock = at("22:00"))(over)?.packageName)
        assertNull(useCase(listOf(leisure), closure = custom, clock = at("22:30"))(over))
        assertNull(useCase(listOf(leisure), closure = custom, clock = at("06:59"))(over))
        assertEquals(social, useCase(listOf(leisure), closure = custom, clock = at("07:00"))(over)?.packageName)
    }

    @Test
    fun `activar el Cierre espera a medianoche, asi que hasta entonces no libera nada`() = runTest {
        val midnight = Instant.parse("2026-10-11T00:00:00Z")
        val pendingOn = FakeDayClosureSettings(
            DayClosureState(effective = DayClosureConfig(enabled = false), pending = PendingClosureConfig(DayClosureConfig(), midnight)),
        )

        assertEquals(social, useCase(listOf(leisure), closure = pendingOn, clock = at("23:00"))(over)?.packageName)
        val afterMidnight = Clock.fixed(Instant.parse("2026-10-11T02:00:00Z"), ZoneOffset.UTC)
        assertNull(useCase(listOf(leisure), closure = pendingOn, clock = afterMidnight)(over))
    }
}
