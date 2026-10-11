package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.model.AppLimit
import com.focusdivergent.app.domain.model.FrictionLevel
import com.focusdivergent.app.domain.model.PendingLimitChange
import com.focusdivergent.app.domain.model.appLimitOf
import com.focusdivergent.app.domain.model.friction
import com.focusdivergent.app.domain.time.startOfTomorrow
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLimitUseCasesTest {

    private val pkg = "com.example.social"
    private val midnight = Instant.parse("2026-10-11T00:00:00Z")

    private fun limit(minutes: Int, friction: FrictionLevel) = appLimitOf(pkg, minutes, friction)

    private class Setup(current: AppLimit?, pending: PendingLimitChange? = null, clock: Clock = TEST_CLOCK) {
        val limits = FakeAppLimits(listOfNotNull(current))
        val pending = FakePendingLimitChanges(listOfNotNull(pending))
        val save = SaveAppLimitUseCase(limits, this.pending, clock)
        val apply = ApplyDueLimitChangesUseCase(limits, this.pending, clock)
    }

    // ---- Guardar

    @Test
    fun `una app sin limite lo recibe al momento`() = runTest {
        val s = Setup(current = null)

        assertEquals(SaveLimitResult.Applied, s.save(pkg, limit(30, FrictionLevel.FLEXIBLE)))

        assertEquals(limit(30, FrictionLevel.FLEXIBLE), s.limits.items[pkg])
        assertTrue(s.pending.items.isEmpty())
    }

    @Test
    fun `endurecer se guarda al momento y no deja nada pendiente`() = runTest {
        val s = Setup(limit(60, FrictionLevel.FLEXIBLE))

        assertEquals(SaveLimitResult.Applied, s.save(pkg, limit(30, FrictionLevel.FIRM)))

        assertEquals(limit(30, FrictionLevel.FIRM), s.limits.items[pkg])
        assertTrue(s.pending.items.isEmpty())
    }

    @Test
    fun `relajar no toca el limite y lo deja pendiente para medianoche`() = runTest {
        val s = Setup(limit(30, FrictionLevel.FIRM))

        val result = s.save(pkg, limit(120, FrictionLevel.FLEXIBLE))

        assertEquals(SaveLimitResult.Scheduled(midnight, partlyApplied = false), result)
        assertEquals(limit(30, FrictionLevel.FIRM), s.limits.items[pkg])
        assertEquals(PendingLimitChange(pkg, limit(120, FrictionLevel.FLEXIBLE), midnight), s.pending.items[pkg])
    }

    @Test
    fun `quitar el limite lo borra en el acto y no deja nada pendiente`() = runTest {
        val s = Setup(limit(30, FrictionLevel.FLEXIBLE))

        assertEquals(SaveLimitResult.Removed, s.save(pkg, null))

        assertNull(s.limits.items[pkg])
        assertTrue(s.pending.items.isEmpty())
    }

    @Test
    fun `quitar el limite se lleva tambien el cambio que esperaba a medianoche`() = runTest {
        val s = Setup(limit(30, FrictionLevel.FIRM), pending = PendingLimitChange(pkg, limit(120, FrictionLevel.FLEXIBLE), midnight))

        assertEquals(SaveLimitResult.Removed, s.save(pkg, null))

        assertNull(s.limits.items[pkg])
        assertTrue("el cambio pendiente ya no tiene sentido", s.pending.items.isEmpty())
    }

    @Test
    fun `quitar el limite funciona igual con cualquier nivel de friccion`() = runTest {
        for (friction in FrictionLevel.entries) {
            val s = Setup(limit(30, friction))

            assertEquals(friction.name, SaveLimitResult.Removed, s.save(pkg, null))
            assertNull(friction.name, s.limits.items[pkg])
        }
    }

    @Test
    fun `quitar el limite de una app que no tiene ninguno no cambia nada`() = runTest {
        val s = Setup(current = null)

        assertEquals(SaveLimitResult.Unchanged, s.save(pkg, null))
        assertTrue(s.limits.items.isEmpty())
    }

    @Test
    fun `quitar un limite no toca los de otras apps`() = runTest {
        val other = "com.example.video"
        val limits = FakeAppLimits(listOf(limit(30, FrictionLevel.FIRM), appLimitOf(other, 60, FrictionLevel.FLEXIBLE)))
        val pending = FakePendingLimitChanges(listOf(PendingLimitChange(other, appLimitOf(other, 90, FrictionLevel.FLEXIBLE), midnight)))

        SaveAppLimitUseCase(limits, pending, TEST_CLOCK)(pkg, null)

        assertEquals(setOf(other), limits.items.keys)
        assertEquals(setOf(other), pending.items.keys)
    }

    @Test
    fun `un cambio mixto aplica ya la parte que endurece`() = runTest {
        val s = Setup(limit(60, FrictionLevel.FIRM))

        val result = s.save(pkg, limit(30, FrictionLevel.FLEXIBLE))

        assertEquals(SaveLimitResult.Scheduled(midnight, partlyApplied = true), result)
        assertEquals(limit(30, FrictionLevel.FIRM), s.limits.items[pkg])
        assertEquals(limit(30, FrictionLevel.FLEXIBLE), s.pending.items.getValue(pkg).newLimit)
    }

    @Test
    fun `pedir lo que ya hay no cambia nada y anula el cambio pendiente`() = runTest {
        val current = limit(30, FrictionLevel.FIRM)
        val s = Setup(current, pending = PendingLimitChange(pkg, limit(60, FrictionLevel.FIRM), midnight))

        assertEquals(SaveLimitResult.Unchanged, s.save(pkg, current))

        assertEquals(current, s.limits.items[pkg])
        assertTrue(s.pending.items.isEmpty())
    }

    @Test
    fun `un cambio nuevo sustituye al pendiente anterior`() = runTest {
        val s = Setup(limit(30, FrictionLevel.FIRM), pending = PendingLimitChange(pkg, limit(120, FrictionLevel.FIRM), midnight))

        s.save(pkg, limit(60, FrictionLevel.FIRM))

        assertEquals(limit(60, FrictionLevel.FIRM), s.pending.items.getValue(pkg).newLimit)
        assertEquals(1, s.pending.items.size)
    }

    @Test
    fun `endurecer despues de pedir una relajacion descarta la relajacion`() = runTest {
        val s = Setup(limit(60, FrictionLevel.FLEXIBLE), pending = PendingLimitChange(pkg, limit(120, FrictionLevel.FLEXIBLE), midnight))

        assertEquals(SaveLimitResult.Applied, s.save(pkg, limit(45, FrictionLevel.FLEXIBLE)))

        assertEquals(45, s.limits.items.getValue(pkg).dailyMinutesAllowed)
        assertTrue("la relajación anterior ya no vale", s.pending.items.isEmpty())
    }

    @Test
    fun `los minutos se quedan entre 5 y 480 y las ampliaciones entre 0 y 3`() = runTest {
        val s = Setup(current = null)

        s.save(pkg, AppLimit(pkg, dailyMinutesAllowed = 1, extensionsPerDay = 9))
        assertEquals(5, s.limits.items.getValue(pkg).dailyMinutesAllowed)
        assertEquals(3, s.limits.items.getValue(pkg).extensionsPerDay)

        val t = Setup(current = null)
        t.save(pkg, AppLimit(pkg, dailyMinutesAllowed = 9_999, extensionsPerDay = -2))
        assertEquals(480, t.limits.items.getValue(pkg).dailyMinutesAllowed)
        assertEquals(0, t.limits.items.getValue(pkg).extensionsPerDay)
    }

    @Test
    fun `medianoche es la proxima medianoche en la zona del usuario`() = runTest {
        // A las 23:30 UTC del sábado ya es domingo en Madrid (UTC+2): la próxima medianoche es la del lunes.
        val lateMadrid = Clock.fixed(Instant.parse("2026-10-10T23:30:00Z"), ZoneId.of("Europe/Madrid"))
        val s = Setup(limit(30, FrictionLevel.FIRM), clock = lateMadrid)

        val result = s.save(pkg, limit(60, FrictionLevel.FIRM)) as SaveLimitResult.Scheduled

        assertEquals(Instant.parse("2026-10-11T22:00:00Z"), result.appliesAt)
        assertEquals(result.appliesAt, lateMadrid.startOfTomorrow())
    }

    // ---- Cancelar

    @Test
    fun `cancelar un cambio pendiente deja el limite como estaba`() = runTest {
        val s = Setup(limit(30, FrictionLevel.FIRM), pending = PendingLimitChange(pkg, limit(60, FrictionLevel.FIRM), midnight))

        CancelPendingLimitChangeUseCase(s.pending)(pkg)

        assertTrue(s.pending.items.isEmpty())
        assertEquals(limit(30, FrictionLevel.FIRM), s.limits.items[pkg])
    }

    // ---- Aplicar a medianoche

    @Test
    fun `antes de medianoche no se aplica nada`() = runTest {
        val s = Setup(limit(30, FrictionLevel.FIRM), pending = PendingLimitChange(pkg, limit(60, FrictionLevel.FLEXIBLE), midnight))

        assertEquals(0, s.apply())

        assertEquals(limit(30, FrictionLevel.FIRM), s.limits.items[pkg])
        assertEquals(1, s.pending.items.size)
    }

    @Test
    fun `a medianoche en punto el cambio entra en vigor y desaparece de pendientes`() = runTest {
        val atMidnight = Clock.fixed(midnight, ZoneOffset.UTC)
        val s = Setup(limit(30, FrictionLevel.FIRM), pending = PendingLimitChange(pkg, limit(60, FrictionLevel.FLEXIBLE), midnight), clock = atMidnight)

        assertEquals(1, s.apply())

        assertEquals(limit(60, FrictionLevel.FLEXIBLE), s.limits.items[pkg])
        assertTrue(s.pending.items.isEmpty())
        assertEquals(FrictionLevel.FLEXIBLE, s.limits.items.getValue(pkg).friction)
    }

    @Test
    fun `si el movil estuvo apagado, el cambio vencido se aplica al volver`() = runTest {
        val later = Clock.fixed(midnight.plus(Duration.ofHours(9)), ZoneOffset.UTC)
        val s = Setup(limit(30, FrictionLevel.FIRM), pending = PendingLimitChange(pkg, limit(60, FrictionLevel.FIRM), midnight), clock = later)

        assertEquals(1, s.apply())

        assertEquals(60, s.limits.items.getValue(pkg).dailyMinutesAllowed)
    }

    @Test
    fun `un cambio de quitar el limite que dejo una version anterior sigue aplicandose a su hora`() = runTest {
        val atMidnight = Clock.fixed(midnight, ZoneOffset.UTC)
        val s = Setup(limit(30, FrictionLevel.FLEXIBLE), pending = PendingLimitChange(pkg, null, midnight), clock = atMidnight)

        s.apply()

        assertNull(s.limits.items[pkg])
        assertTrue(s.pending.items.isEmpty())
    }

    @Test
    fun `un cambio de quitar un limite que ya no existe no falla`() = runTest {
        val atMidnight = Clock.fixed(midnight, ZoneOffset.UTC)
        val s = Setup(current = null, pending = PendingLimitChange(pkg, null, midnight), clock = atMidnight)

        assertEquals(1, s.apply())

        assertTrue(s.pending.items.isEmpty())
    }

    @Test
    fun `aplica cada app a su hora y deja las que aun no han llegado`() = runTest {
        val other = "com.example.video"
        val atMidnight = Clock.fixed(midnight, ZoneOffset.UTC)
        val limits = FakeAppLimits(listOf(limit(30, FrictionLevel.FIRM), appLimitOf(other, 30, FrictionLevel.FIRM)))
        val pending = FakePendingLimitChanges(
            listOf(
                PendingLimitChange(pkg, limit(60, FrictionLevel.FIRM), midnight),
                PendingLimitChange(other, appLimitOf(other, 90, FrictionLevel.FIRM), midnight.plusSeconds(86_400)),
            ),
        )

        assertEquals(1, ApplyDueLimitChangesUseCase(limits, pending, atMidnight)())

        assertEquals(60, limits.items.getValue(pkg).dailyMinutesAllowed)
        assertEquals(30, limits.items.getValue(other).dailyMinutesAllowed)
        assertEquals(setOf(other), pending.items.keys)
    }
}
