package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.model.AppLimit
import com.focusdivergent.app.domain.model.BlockReason
import com.focusdivergent.app.domain.model.BlockedApp
import com.focusdivergent.app.domain.model.ObligationReminder
import com.focusdivergent.app.domain.model.RealityCheck
import com.focusdivergent.app.domain.model.Task
import com.focusdivergent.app.domain.model.UsageSnapshot
import com.focusdivergent.app.domain.realitycheck.RealityCheckSilences
import com.focusdivergent.app.domain.reminder.ReminderSnoozes
import java.time.Clock
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Sin ninguna Lanzadera en curso: estos tests no la usan. */
private object NoLaunchStore : com.focusdivergent.app.domain.launch.FocusLaunchStore {
    override fun current() = null
    override fun save(session: com.focusdivergent.app.domain.model.FocusLaunchSession) = Unit
    override fun clear() = Unit
}

class EvaluateInterruptionUseCaseTest {

    private val social = "com.example.social"
    private val minute = 60_000L

    // Una app que ya ha agotado su límite de 10 minutos: sin exenciones, esto se bloquearía.
    private val overLimit = UsageSnapshot(foregroundPackage = social, usedMillisByPackage = mapOf(social to 12 * minute))

    private class Setup(
        snapshot: UsageSnapshot,
        val device: FakeDeviceState,
        tasks: List<Task>,
        limit: AppLimit,
        clock: Clock,
    ) {
        val usage = FakeUsage(snapshot)
        private val limits = FakeAppLimits(listOf(limit))
        val useCase = EvaluateInterruptionUseCase(
            usage = usage,
            device = device,
            findObligationReminder = FindObligationReminderUseCase(FakeTasks(tasks), ReminderSnoozes(), clock),
            findFocusLaunch = FindFocusLaunchUseCase(NoLaunchStore, clock),
            findBlockedApp = FindBlockedAppUseCase(
                limits = limits,
                extensions = FakeTimeExtensions(),
                closure = FakeDayClosureSettings(),
                clock = clock,
                ownPackage = "com.focusdivergent.app",
            ),
            findRealityCheck = FindRealityCheckUseCase(
                limits = limits,
                silences = RealityCheckSilences(),
                settings = FakeRealityCheckSettings(),
                clock = clock,
                ownPackage = "com.focusdivergent.app",
            ),
        )
    }

    private fun setup(
        device: FakeDeviceState = FakeDeviceState(),
        snapshot: UsageSnapshot = overLimit,
        tasks: List<Task> = emptyList(),
        limit: AppLimit = AppLimit("com.example.social", 10),
        clock: Clock = TEST_CLOCK,
    ) = Setup(snapshot, device, tasks, limit, clock)

    // 23:00 UTC del 10 de octubre: la noche del Cierre de Jornada, once horas después de TEST_NOW.
    private val night: Clock = Clock.fixed(TEST_NOW.plusSeconds(11 * 3600), ZoneOffset.UTC)
    private val leisureLimit = AppLimit("com.example.social", 10, isLeisure = true)

    @Test
    fun `de noche una app de ocio agotada no se bloquea`() = runTest {
        assertNull(setup(limit = leisureLimit, clock = night).useCase())
    }

    @Test
    fun `de noche el Reality Check sigue funcionando en una app de ocio`() = runTest {
        // El cierre solo levanta el bloqueo por límite: veinte minutos seguidos siguen preguntando.
        val result = setup(snapshot = longSession(), limit = leisureLimit, clock = night).useCase()

        assertEquals(RealityCheck(social, 25 * minute), result)
    }

    @Test
    fun `de noche el aviso de una obligacion sigue saltando en una app de ocio`() = runTest {
        val result = setup(
            limit = leisureLimit,
            clock = night,
            tasks = listOf(obligation(1, startsInMinutes = 11 * 60 + 6, title = "Cita")),
        ).useCase()

        assertEquals(1L, (result as ObligationReminder).taskId)
    }

    @Test
    fun `de noche una app que no es de ocio sigue bloqueada`() = runTest {
        assertEquals(social, (setup(clock = night).useCase() as BlockedApp).packageName)
    }

    private fun longSession() = UsageSnapshot(social, mapOf(social to 5 * minute), foregroundSessionMillis = 25 * minute)

    @Test
    fun `con la pantalla encendida y la app agotada devuelve el bloqueo`() = runTest {
        val result = setup().useCase()

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
    fun `con la pantalla apagada no interrumpe ni consulta el uso`() = runTest {
        val s = setup(FakeDeviceState(interactive = false))

        assertNull(s.useCase())
        assertEquals(0, s.usage.reads)
    }

    @Test
    fun `durante una llamada no interrumpe ni consulta el uso`() = runTest {
        val s = setup(FakeDeviceState(inCall = true))

        assertNull(s.useCase())
        assertEquals(0, s.usage.reads)
    }

    @Test
    fun `con el marcador del telefono en primer plano no interrumpe`() = runTest {
        val dialer = "com.samsung.android.dialer"
        val s = setup(
            device = FakeDeviceState(safe = setOf(dialer)),
            snapshot = UsageSnapshot(dialer, mapOf(dialer to 99 * minute, social to 99 * minute)),
        )

        assertNull(s.useCase())
    }

    @Test
    fun `una app que no es segura sigue bloqueandose aunque haya paquetes seguros`() = runTest {
        val s = setup(device = FakeDeviceState(safe = setOf("com.samsung.android.dialer")))

        assertEquals(social, (s.useCase() as BlockedApp).packageName)
    }

    @Test
    fun `el aviso de una obligacion tiene prioridad sobre el bloqueo de la app`() = runTest {
        val result = setup(tasks = listOf(obligation(1, startsInMinutes = 6, title = "Cita médica"))).useCase()

        assertEquals(ObligationReminder(1, "Cita médica", TEST_NOW.plusSeconds(6 * 60)), result)
    }

    @Test
    fun `sin obligaciones a la vista sigue bloqueando la app`() = runTest {
        val result = setup(tasks = listOf(obligation(1, startsInMinutes = 90))).useCase()

        assertEquals(social, (result as BlockedApp).packageName)
    }

    @Test
    fun `el aviso de una obligacion respeta la pantalla apagada y las llamadas`() = runTest {
        val due = listOf(obligation(1, startsInMinutes = 6))

        assertNull(setup(FakeDeviceState(interactive = false), tasks = due).useCase())
        assertNull(setup(FakeDeviceState(inCall = true), tasks = due).useCase())
    }

    @Test
    fun `el aviso de una obligacion no tapa el marcador del telefono`() = runTest {
        val dialer = "com.samsung.android.dialer"
        val s = setup(
            device = FakeDeviceState(safe = setOf(dialer)),
            snapshot = UsageSnapshot(dialer, emptyMap()),
            tasks = listOf(obligation(1, startsInMinutes = 6)),
        )

        assertNull(s.useCase())
    }

    // Dentro del límite (5 de 10 min) pero con 25 minutos seguidos en la app.
    private val longSession = UsageSnapshot(social, mapOf(social to 5 * minute), foregroundSessionMillis = 25 * minute)

    @Test
    fun `veinte minutos seguidos en una app con limite sin agotar lanzan el Reality Check`() = runTest {
        val result = setup(snapshot = longSession).useCase()

        assertEquals(RealityCheck(social, 25 * minute), result)
    }

    @Test
    fun `el bloqueo tiene prioridad sobre el Reality Check`() = runTest {
        val exhaustedAndLong = longSession.copy(usedMillisByPackage = mapOf(social to 12 * minute))

        assertEquals(social, (setup(snapshot = exhaustedAndLong).useCase() as BlockedApp).packageName)
    }

    @Test
    fun `el aviso de una obligacion tiene prioridad sobre el Reality Check`() = runTest {
        val result = setup(snapshot = longSession, tasks = listOf(obligation(1, startsInMinutes = 5))).useCase()

        assertEquals(1L, (result as ObligationReminder).taskId)
    }

    @Test
    fun `el Reality Check respeta la pantalla apagada, las llamadas y el marcador`() = runTest {
        assertNull(setup(FakeDeviceState(interactive = false), snapshot = longSession).useCase())
        assertNull(setup(FakeDeviceState(inCall = true), snapshot = longSession).useCase())
        assertNull(setup(FakeDeviceState(safe = setOf(social)), snapshot = longSession).useCase())
    }

    @Test
    fun `sin nada que interrumpir devuelve nulo`() = runTest {
        val s = setup(snapshot = UsageSnapshot(social, mapOf(social to 2 * minute)))

        assertNull(s.useCase())
    }
}
