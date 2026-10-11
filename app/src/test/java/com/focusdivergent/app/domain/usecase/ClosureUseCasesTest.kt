package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.closure.ClosureRecord
import com.focusdivergent.app.domain.closure.DayClosureConfig
import com.focusdivergent.app.domain.closure.DayClosureState
import com.focusdivergent.app.domain.closure.PendingClosureConfig
import com.focusdivergent.app.domain.model.AppLimit
import com.focusdivergent.app.domain.model.BrainDump
import com.focusdivergent.app.domain.model.TaskStatus
import com.focusdivergent.app.domain.model.TaskType
import com.focusdivergent.app.domain.model.UsageSnapshot
import com.focusdivergent.app.domain.reward.RewardCause
import com.focusdivergent.app.domain.reward.RewardEvent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El Cierre de Jornada: qué se traslada, cuándo toca cerrar, que cada día se cierre una vez, el resumen y la alarma.
 * Hoy es el sábado 10 de octubre de 2026 (UTC); el cierre de serie es a las 21:30.
 */
class ClosureUseCasesTest {

    private val social = "com.example.social"
    private val video = "com.example.video"
    private val minute = 60_000L
    private val today = LocalDate.of(2026, 10, 10)

    private val clock = MutableClock(Instant.parse("2026-10-10T21:30:00Z"))
    private val tasks = FakeTasks()
    private val completions = FakeRoutineCompletions()
    private val rewards = FakeRewards()
    private val limits = FakeAppLimits()
    private val brainDump = FakeBrainDump()
    private val store = FakeDayClosureStore()
    private val notifier = FakeClosureNotifier()
    private val settings = FakeDayClosureSettings()
    private var snapshot = UsageSnapshot(null, mapOf("com.example.other" to 1 * minute))

    private val ledger get() = testLedger(completions, rewards, clock = clock)
    private val run get() = RunDayClosureUseCase(settings, store, tasks, limits, FakeUsageOf { snapshot }, brainDump, ledger, notifier, clock)

    /** Un uso de apps que devuelve lo que valga [snapshot] en cada lectura. */
    private class FakeUsageOf(private val provider: () -> UsageSnapshot) : com.focusdivergent.app.domain.repository.UsageRepository {
        override suspend fun readToday(): UsageSnapshot = provider()
    }

    private fun at(text: String) = Instant.parse("2026-10-10T${text}:00Z")
    private fun tomorrow(text: String) = Instant.parse("2026-10-11T${text}:00Z")

    // ---- Qué se traslada a mañana

    @Test
    fun `una tarea con fecha de hoy sin hacer pasa a mañana a la misma hora`() = runTest {
        tasks.save(task(1, title = "Informe").copy(startTime = at("17:45")))

        run()

        assertEquals(tomorrow("17:45"), tasks.getById(1)?.startTime)
        assertEquals(TaskStatus.PENDING, tasks.getById(1)?.status)
    }

    @Test
    fun `la hora de fin se traslada con la de inicio`() = runTest {
        tasks.save(task(1).copy(startTime = at("10:00"), endTime = at("11:30")))

        run()

        assertEquals(tomorrow("10:00"), tasks.getById(1)?.startTime)
        assertEquals(tomorrow("11:30"), tasks.getById(1)?.endTime)
    }

    @Test
    fun `una tarea en marcha tambien pasa a mañana`() = runTest {
        tasks.save(task(1, status = TaskStatus.IN_PROGRESS).copy(startTime = at("09:00")))

        run()

        assertEquals(tomorrow("09:00"), tasks.getById(1)?.startTime)
        assertEquals(TaskStatus.IN_PROGRESS, tasks.getById(1)?.status)
    }

    @Test
    fun `lo ya hecho no se mueve`() = runTest {
        tasks.save(task(1, status = TaskStatus.DONE, completedAt = at("12:00")).copy(startTime = at("09:00")))

        run()

        assertEquals(at("09:00"), tasks.getById(1)?.startTime)
    }

    @Test
    fun `las tareas sin fecha, las obligaciones y las rutinas no se tocan`() = runTest {
        tasks.save(task(1))
        tasks.save(obligation(2, startsInMinutes = 6 * 60)) // empieza a las 18:00
        tasks.save(task(3, type = TaskType.ROUTINE, recurrenceRule = "FREQ=DAILY").copy(startTime = at("08:00")))

        run()

        assertNull(tasks.getById(1)?.startTime)
        assertEquals(TEST_NOW.plusSeconds(6 * 3600), tasks.getById(2)?.startTime)
        assertEquals(at("08:00"), tasks.getById(3)?.startTime)
    }

    @Test
    fun `solo se trasladan las de hoy, no las de otros dias`() = runTest {
        tasks.save(task(1).copy(startTime = Instant.parse("2026-10-09T10:00:00Z")))
        tasks.save(task(2).copy(startTime = Instant.parse("2026-10-11T10:00:00Z")))
        tasks.save(task(3).copy(startTime = Instant.parse("2026-10-10T00:00:00Z")))
        tasks.save(task(4).copy(startTime = Instant.parse("2026-10-10T23:59:00Z")))

        run()

        assertEquals(Instant.parse("2026-10-09T10:00:00Z"), tasks.getById(1)?.startTime)
        assertEquals(Instant.parse("2026-10-11T10:00:00Z"), tasks.getById(2)?.startTime)
        assertEquals(Instant.parse("2026-10-11T00:00:00Z"), tasks.getById(3)?.startTime)
        assertEquals(Instant.parse("2026-10-11T23:59:00Z"), tasks.getById(4)?.startTime)
    }

    @Test
    fun `el cierre anota cuantas tareas se trasladaron`() = runTest {
        tasks.save(task(1).copy(startTime = at("10:00")))
        tasks.save(task(2).copy(startTime = at("11:00")))
        tasks.save(task(3))

        run()

        assertEquals(2, store.record.value?.moved)
    }

    // ---- Cuándo toca cerrar

    @Test
    fun `a la hora de cierre se cierra el dia de hoy y se avisa`() = runTest {
        brainDump.add(BrainDump(content = "idea", timestamp = TEST_NOW))

        val closed = run()

        assertEquals(today, closed)
        assertEquals(today, store.record.value?.day)
        assertEquals(listOf(today to 1), notifier.shown)
    }

    @Test
    fun `antes de la hora de cierre no se cierra nada`() = runTest {
        clock.now = at("21:29")
        tasks.save(task(1).copy(startTime = at("10:00")))

        assertNull(run())

        assertEquals(at("10:00"), tasks.getById(1)?.startTime)
        assertTrue(notifier.shown.isEmpty())
        assertNull(store.record.value)
    }

    @Test
    fun `de dia no se cierra nada aunque no se hubiera cerrado ayer`() = runTest {
        clock.now = at("12:00")

        assertNull(run())
    }

    @Test
    fun `cada dia se cierra una sola vez`() = runTest {
        tasks.save(task(1).copy(startTime = at("10:00")))

        assertEquals(today, run())
        clock.now = at("22:45")
        assertNull(run())
        assertNull(run())

        assertEquals(1, notifier.shown.size)
    }

    @Test
    fun `si la alarma llega tarde pero sigue la noche, se cierra igual`() = runTest {
        clock.now = at("23:50")

        assertEquals(today, run())
    }

    @Test
    fun `si no se cerro por la noche, en la madrugada se cierra el dia anterior`() = runTest {
        clock.now = tomorrow("01:10")
        tasks.save(task(1).copy(startTime = at("20:00")))

        val closed = run()

        assertEquals(today, closed)
        assertEquals(tomorrow("20:00"), tasks.getById(1)?.startTime)
    }

    @Test
    fun `tras cerrar a medianoche no se vuelve a cerrar en la madrugada`() = runTest {
        run()
        clock.now = tomorrow("02:00")

        assertNull(run())
    }

    @Test
    fun `al dia siguiente se cierra el nuevo dia`() = runTest {
        run()
        clock.now = tomorrow("21:31")

        assertEquals(LocalDate.of(2026, 10, 11), run())
        assertEquals(2, notifier.shown.size)
    }

    @Test
    fun `con el cierre desactivado no hace nada`() = runTest {
        settings.update(DayClosureState(effective = DayClosureConfig(enabled = false)))
        tasks.save(task(1).copy(startTime = at("10:00")))

        assertNull(run())

        assertEquals(at("10:00"), tasks.getById(1)?.startTime)
        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun `usa la hora configurada y respeta un cambio pendiente que ya llego`() = runTest {
        // Desactivado, con un cambio que lo activa desde la medianoche del 10: a las 21:30 del 10 ya rige.
        settings.update(
            DayClosureState(
                effective = DayClosureConfig(enabled = false),
                pending = PendingClosureConfig(DayClosureConfig(closeAt = LocalTime.of(22, 0)), Instant.parse("2026-10-10T00:00:00Z")),
            ),
        )

        assertNull(run())
        clock.now = at("22:00")
        assertEquals(today, run())
    }

    @Test
    fun `cerrar hoy a la fuerza cierra aunque no sea la hora ni haya cerrado ya`() = runTest {
        clock.now = at("12:00")
        tasks.save(task(1).copy(startTime = at("10:00")))
        store.save(ClosureRecord(today, moved = 0, respectedLimits = 0))

        assertEquals(today, run.closeToday())

        assertEquals(tomorrow("10:00"), tasks.getById(1)?.startTime)
        assertEquals(1, notifier.shown.size)
    }

    // ---- XP por respetar un límite

    @Test
    fun `cada app cuyo limite no se gasto da 5 XP al cerrar`() = runTest {
        limits.save(AppLimit(social, 30))
        limits.save(AppLimit(video, 30))
        snapshot = UsageSnapshot(null, mapOf(social to 10 * minute, video to 45 * minute))

        run()

        val events = rewards.events.filter { it.cause == RewardCause.LIMIT_RESPECTED }
        assertEquals(1, events.size)
        assertEquals(5, events.single().xp)
        assertEquals(today, events.single().day)
        assertEquals(1, store.record.value?.respectedLimits)
    }

    @Test
    fun `gastar justo el limite todavia cuenta como respetarlo`() = runTest {
        limits.save(AppLimit(social, 30))
        snapshot = UsageSnapshot(null, mapOf(social to 30 * minute))

        run()

        assertEquals(1, rewards.events.count { it.cause == RewardCause.LIMIT_RESPECTED })
    }

    @Test
    fun `una app sin uso hoy cuenta si se midio el uso de otras apps`() = runTest {
        limits.save(AppLimit(social, 30))

        run()

        assertEquals(1, rewards.events.count { it.cause == RewardCause.LIMIT_RESPECTED })
    }

    @Test
    fun `las apps cerradas no tienen limite que respetar y no dan XP`() = runTest {
        limits.save(AppLimit(social, 30, isStrictlyBlocked = true, extensionsPerDay = 0))

        run()

        assertEquals(0, rewards.events.count { it.cause == RewardCause.LIMIT_RESPECTED })
    }

    @Test
    fun `sin poder leer el uso no se da ni se quita nada`() = runTest {
        limits.save(AppLimit(social, 30))
        snapshot = UsageSnapshot(null, emptyMap())

        run()

        assertTrue(rewards.events.isEmpty())
        assertEquals(0, store.record.value?.respectedLimits)
    }

    @Test
    fun `si leer el uso falla el cierre se hace igual, sin XP`() = runTest {
        limits.save(AppLimit(social, 30))
        tasks.save(task(1).copy(startTime = at("10:00")))
        val failing = object : com.focusdivergent.app.domain.repository.UsageRepository {
            override suspend fun readToday(): UsageSnapshot = throw SecurityException("sin permiso")
        }

        val closed = RunDayClosureUseCase(settings, store, tasks, limits, failing, brainDump, ledger, notifier, clock)()

        assertEquals(today, closed)
        assertEquals(tomorrow("10:00"), tasks.getById(1)?.startTime)
        assertTrue(rewards.events.isEmpty())
    }

    @Test
    fun `al cerrar la madrugada siguiente no se mide el uso, que ya es de otro dia`() = runTest {
        clock.now = tomorrow("01:00")
        limits.save(AppLimit(social, 30))

        run()

        assertTrue(rewards.events.isEmpty())
    }

    @Test
    fun `repetir el cierre forzado no suma dos veces el XP de los limites`() = runTest {
        limits.save(AppLimit(social, 30))

        run.closeToday()
        run.closeToday()

        assertEquals(1, rewards.events.count { it.cause == RewardCause.LIMIT_RESPECTED })
    }

    // ---- Resumen del dia

    private val summary get() = ObserveDaySummaryUseCase(tasks, completions, rewards, brainDump, store, clock)

    @Test
    fun `el resumen cuenta lo hecho ese dia, con sus titulos`() = runTest {
        tasks.save(task(1, title = "Informe", status = TaskStatus.DONE, completedAt = at("11:00")))
        tasks.save(task(2, title = "Compra", status = TaskStatus.DONE, completedAt = at("09:00")))
        tasks.save(task(3, title = "Ayer", status = TaskStatus.DONE, completedAt = Instant.parse("2026-10-09T09:00:00Z")))
        tasks.save(task(4, title = "Pendiente"))
        tasks.save(task(5, title = "Medicación", type = TaskType.ROUTINE, recurrenceRule = "FREQ=DAILY"))
        completions.markCompleted(5, today, TEST_NOW)

        val s = summary(today).first()

        assertEquals(2, s.tasksDone)
        assertEquals(1, s.routinesDone)
        assertEquals(3, s.doneCount)
        assertEquals(listOf("Compra", "Informe", "Medicación"), s.doneTitles)
        assertTrue(!s.isQuiet)
    }

    @Test
    fun `el resumen incluye los minutos de enfoque, el XP y las monedas del dia`() = runTest {
        rewards.record(RewardEvent(RewardCause.FOCUS_SESSION, 1, today, xp = 15, coins = 1, minutes = 45))
        rewards.record(RewardEvent(RewardCause.TASK_DONE, 2, today, xp = 10, coins = 1))
        rewards.record(RewardEvent(RewardCause.TASK_DONE, 3, today.minusDays(1), xp = 10, coins = 1, minutes = 0))

        val s = summary(today).first()

        assertEquals(45, s.focusMinutes)
        assertEquals(25, s.xp)
        assertEquals(2, s.coins)
    }

    @Test
    fun `un dia sin nada hecho es tranquilo y no enseña numeros`() = runTest {
        val s = summary(today).first()

        assertTrue(s.isQuiet)
        assertEquals(0, s.doneCount)
        assertEquals(emptyList<String>(), s.doneTitles)
    }

    @Test
    fun `solo con enfoque el dia no es tranquilo`() = runTest {
        rewards.record(RewardEvent(RewardCause.FOCUS_SESSION, 1, today, xp = 5, coins = 1, minutes = 25))

        assertTrue(!summary(today).first().isQuiet)
    }

    @Test
    fun `el resumen enseña lo trasladado y lo respetado solo del dia que se cerro`() = runTest {
        store.save(ClosureRecord(today, moved = 3, respectedLimits = 2))

        val closed = summary(today).first()
        val other = summary(today.minusDays(1)).first()

        assertEquals(3, closed.moved)
        assertEquals(2, closed.respectedLimits)
        assertEquals(0, other.moved)
        assertEquals(0, other.respectedLimits)
    }

    @Test
    fun `el resumen cuenta las ideas que esperan en la bandeja, no las procesadas`() = runTest {
        brainDump.add(BrainDump(content = "una", timestamp = TEST_NOW))
        brainDump.add(BrainDump(content = "dos", timestamp = TEST_NOW))
        brainDump.add(BrainDump(content = "ya vista", timestamp = TEST_NOW, isProcessed = true))

        assertEquals(2, summary(today).first().ideasWaiting)
    }

    @Test
    fun `el resumen no cuenta como hecho lo que se trasladó`() = runTest {
        tasks.save(task(1).copy(startTime = at("10:00")))
        run()

        assertEquals(0, summary(today).first().doneCount)
    }

    // ---- Alarma

    private val alarms = FakeClosureAlarms()
    private val schedule get() = ScheduleDayClosureUseCase(settings, alarms, clock)

    @Test
    fun `la alarma se pone a la proxima hora de cierre`() {
        clock.now = at("12:00")

        schedule()

        assertEquals(at("21:30"), alarms.scheduledAt)
    }

    @Test
    fun `pasada la hora de hoy la alarma es para mañana`() {
        clock.now = at("21:30")

        schedule()

        assertEquals(tomorrow("21:30"), alarms.scheduledAt)
    }

    @Test
    fun `con otra hora configurada la alarma la sigue`() {
        settings.update(DayClosureState(effective = DayClosureConfig(closeAt = LocalTime.of(22, 30))))
        clock.now = at("12:00")

        schedule()

        assertEquals(at("22:30"), alarms.scheduledAt)
    }

    @Test
    fun `desactivado no hay alarma`() {
        settings.update(DayClosureState(effective = DayClosureConfig(enabled = false)))
        clock.now = at("12:00")

        schedule()

        assertNull(alarms.scheduledAt)
        assertEquals(1, alarms.cancels)
    }

    @Test
    fun `un cambio que activa el cierre a medianoche pone la alarma de mañana, no la de hoy`() {
        settings.update(
            DayClosureState(
                effective = DayClosureConfig(enabled = false),
                pending = PendingClosureConfig(DayClosureConfig(), Instant.parse("2026-10-11T00:00:00Z")),
            ),
        )
        clock.now = at("12:00")

        schedule()

        assertEquals(tomorrow("21:30"), alarms.scheduledAt)
    }

    @Test
    fun `un cambio que desactiva el cierre a medianoche no quita la alarma de hoy hasta entonces`() {
        // Desactivar endurece y rige al momento, así que esto solo pasa con un cambio ya programado.
        settings.update(
            DayClosureState(
                effective = DayClosureConfig(),
                pending = PendingClosureConfig(DayClosureConfig(enabled = false), Instant.parse("2026-10-11T00:00:00Z")),
            ),
        )
        clock.now = at("12:00")

        schedule()

        assertEquals(at("21:30"), alarms.scheduledAt)
    }

    // ---- Cambiar los ajustes

    private val change get() = ChangeClosureSettingsUseCase(settings, schedule, clock)

    @Test
    fun `desactivar el cierre rige al momento y quita la alarma`() {
        clock.now = at("12:00")
        schedule()

        change(DayClosureConfig(enabled = false))

        assertEquals(DayClosureState(effective = DayClosureConfig(enabled = false)), settings.state.value)
        assertNull(alarms.scheduledAt)
    }

    @Test
    fun `activar el cierre espera a medianoche y la alarma sale para mañana`() {
        clock.now = at("12:00")
        settings.update(DayClosureState(effective = DayClosureConfig(enabled = false)))

        change(DayClosureConfig())

        assertEquals(DayClosureConfig(enabled = false), settings.state.value.effective)
        assertEquals(PendingClosureConfig(DayClosureConfig(), tomorrow("00:00")), settings.state.value.pending)
        assertEquals(tomorrow("21:30"), alarms.scheduledAt)
    }

    @Test
    fun `cerrar mas tarde rige ya y la alarma se mueve`() {
        clock.now = at("12:00")

        change(DayClosureConfig(closeAt = LocalTime.of(23, 0)))

        assertEquals(LocalTime.of(23, 0), settings.state.value.effective.closeAt)
        assertEquals(at("23:00"), alarms.scheduledAt)
    }

    @Test
    fun `cerrar antes espera a medianoche`() {
        clock.now = at("12:00")

        change(DayClosureConfig(closeAt = LocalTime.of(20, 0)))

        assertEquals(LocalTime.of(21, 30), settings.state.value.effective.closeAt)
        assertEquals(LocalTime.of(20, 0), settings.state.value.pending?.config?.closeAt)
        assertEquals(at("21:30"), alarms.scheduledAt)
    }

    @Test
    fun `volver a lo que rige borra el cambio pendiente`() {
        clock.now = at("12:00")
        change(DayClosureConfig(closeAt = LocalTime.of(20, 0)))

        change(DayClosureConfig())

        assertNull(settings.state.value.pending)
    }

    @Test
    fun `cancelar el cambio pendiente deja lo que rige`() {
        clock.now = at("12:00")
        change(DayClosureConfig(closeAt = LocalTime.of(20, 0)))

        change.cancelPending()

        assertEquals(DayClosureState(effective = DayClosureConfig()), settings.state.value)
        assertEquals(at("21:30"), alarms.scheduledAt)
    }

    @Test
    fun `tras llegar la medianoche pedir un cambio parte de lo que ya rige`() {
        // El cambio pendiente (cerrar a las 20:00) ya es lo vigente: pedir las 22:00 endurece y rige ya.
        settings.update(
            DayClosureState(
                effective = DayClosureConfig(),
                pending = PendingClosureConfig(DayClosureConfig(closeAt = LocalTime.of(20, 0)), Instant.parse("2026-10-10T00:00:00Z")),
            ),
        )
        clock.now = at("12:00")

        change(DayClosureConfig(closeAt = LocalTime.of(22, 0)))

        assertEquals(DayClosureState(effective = DayClosureConfig(closeAt = LocalTime.of(22, 0))), settings.state.value)
    }
}
