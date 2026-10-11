package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.model.AppLimit
import com.focusdivergent.app.domain.model.RealityCheck
import com.focusdivergent.app.domain.model.UsageSnapshot
import com.focusdivergent.app.domain.realitycheck.RealityCheckSilences
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FindRealityCheckUseCaseTest {

    private val social = "com.example.social"
    private val own = "com.focusdivergent.app"
    private val minute = 60_000L

    private val silences = RealityCheckSilences()
    private val settings = FakeRealityCheckSettings()

    private fun find(
        limit: AppLimit? = AppLimit(social, 30),
        clock: Clock = TEST_CLOCK,
    ) = FindRealityCheckUseCase(FakeAppLimits(listOfNotNull(limit)), silences, settings, clock, ownPackage = own)

    private fun snapshot(sessionMinutes: Long, pkg: String? = social) =
        UsageSnapshot(pkg, mapOf(social to 5 * minute), foregroundSessionMillis = sessionMinutes * minute)

    @Test
    fun `justo a los veinte minutos seguidos pregunta`() = runTest {
        assertEquals(RealityCheck(social, 20 * minute), find()(snapshot(20)))
    }

    @Test
    fun `con menos de veinte minutos no pregunta`() = runTest {
        val almost = snapshot(20).copy(foregroundSessionMillis = 20 * minute - 1)

        assertNull(find()(almost))
        assertNull(find()(snapshot(0)))
    }

    @Test
    fun `sin app en primer plano no pregunta`() = runTest {
        assertNull(find()(snapshot(40, pkg = null)))
    }

    @Test
    fun `una app sin limite no tiene Reality Check`() = runTest {
        assertNull(find(limit = null)(snapshot(40)))
    }

    @Test
    fun `una app con bloqueo estricto no tiene Reality Check`() = runTest {
        assertNull(find(limit = AppLimit(social, 30, isStrictlyBlocked = true))(snapshot(40)))
    }

    @Test
    fun `la propia app nunca tiene Reality Check`() = runTest {
        assertNull(find(limit = AppLimit(own, 30))(snapshot(40, pkg = own)))
    }

    @Test
    fun `tras responder que si, no vuelve a preguntar hasta pasado el silencio`() = runTest {
        AcknowledgeRealityCheckUseCase(silences, settings, TEST_CLOCK)(social)

        assertNull(find()(snapshot(25)))

        val justBefore = Clock.fixed(TEST_NOW.plus(Duration.ofMinutes(20)).minusMillis(1), TEST_CLOCK.zone)
        assertNull(find(clock = justBefore)(snapshot(44)))

        val after = Clock.fixed(TEST_NOW.plus(Duration.ofMinutes(20)), TEST_CLOCK.zone)
        assertEquals(RealityCheck(social, 45 * minute), find(clock = after)(snapshot(45)))
    }

    @Test
    fun `el silencio es de una app y no afecta a las demas`() = runTest {
        val video = "com.example.video"
        AcknowledgeRealityCheckUseCase(silences, settings, TEST_CLOCK)(social)
        val useCase = FindRealityCheckUseCase(
            FakeAppLimits(listOf(AppLimit(social, 30), AppLimit(video, 30))),
            silences, settings, TEST_CLOCK, own,
        )

        assertEquals(video, useCase(UsageSnapshot(video, emptyMap(), 25 * minute))?.packageName)
    }

    @Test
    fun `el umbral y el silencio salen de los ajustes`() = runTest {
        settings.thresholdMinutes = 2
        settings.silenceMinutes = 3

        assertEquals(RealityCheck(social, 2 * minute), find()(snapshot(2)))

        AcknowledgeRealityCheckUseCase(silences, settings, TEST_CLOCK)(social)
        val twoMinutesLater = Clock.fixed(TEST_NOW.plusSeconds(179), TEST_CLOCK.zone)
        assertNull(find(clock = twoMinutesLater)(snapshot(5)))
        val threeMinutesLater = Clock.fixed(TEST_NOW.plusSeconds(180), TEST_CLOCK.zone)
        assertEquals(social, find(clock = threeMinutesLater)(snapshot(5))?.packageName)
    }
}
