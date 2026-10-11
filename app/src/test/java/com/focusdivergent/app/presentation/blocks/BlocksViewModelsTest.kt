package com.focusdivergent.app.presentation.blocks

import androidx.lifecycle.SavedStateHandle
import com.focusdivergent.app.domain.device.DeviceState
import com.focusdivergent.app.domain.model.AppLimit
import com.focusdivergent.app.domain.model.FrictionLevel
import com.focusdivergent.app.domain.model.InstalledApp
import com.focusdivergent.app.domain.model.PendingLimitChange
import com.focusdivergent.app.domain.model.UsageSnapshot
import com.focusdivergent.app.domain.model.appLimitOf
import com.focusdivergent.app.domain.usecase.ApplyDueLimitChangesUseCase
import com.focusdivergent.app.domain.usecase.CancelPendingLimitChangeUseCase
import com.focusdivergent.app.domain.usecase.FakeAppLimits
import com.focusdivergent.app.domain.usecase.FakeDeviceState
import com.focusdivergent.app.domain.usecase.FakeInstalledApps
import com.focusdivergent.app.domain.usecase.FakePendingLimitChanges
import com.focusdivergent.app.domain.usecase.FakeUsage
import com.focusdivergent.app.domain.usecase.SaveAppLimitUseCase
import com.focusdivergent.app.domain.usecase.TEST_CLOCK
import com.focusdivergent.app.presentation.MainDispatcherRule
import com.focusdivergent.app.service.BlockerStatus
import java.time.Instant
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BlocksViewModelsTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val minute = 60_000L
    private val midnight = Instant.parse("2026-10-11T00:00:00Z")
    private val social = "com.example.social"
    private val video = "com.example.video"
    private val own = "com.focusdivergent.app"
    private val dialer = "com.samsung.android.dialer"

    private val apps = FakeInstalledApps(
        listOf(
            InstalledApp(social, "Redes"),
            InstalledApp(video, "Vídeo"),
            InstalledApp("com.example.camera", "Cámara"),
            InstalledApp(own, "FocusDivergent"),
            InstalledApp(dialer, "Teléfono"),
        ),
    )

    private fun limit(pkg: String, minutes: Int, friction: FrictionLevel) = appLimitOf(pkg, minutes, friction)

    private fun usage(vararg used: Pair<String, Long>) = FakeUsage(UsageSnapshot(null, mapOf(*used)))

    private fun <T> TestScope.observe(flow: kotlinx.coroutines.flow.Flow<T>) {
        backgroundScope.launch(mainDispatcher.dispatcher) { flow.collect() }
    }

    // ---- Lista de límites

    private fun blocks(
        limits: FakeAppLimits,
        pending: FakePendingLimitChanges = FakePendingLimitChanges(),
        status: BlockerStatus = BlockerStatus(),
    ) = BlocksViewModel(
        limits = limits,
        pending = pending,
        usage = usage(social to 12 * minute),
        installedApps = apps,
        blockerStatus = status,
        applyDueLimitChanges = ApplyDueLimitChangesUseCase(limits, pending, TEST_CLOCK),
    )

    @Test
    fun `la lista trae cada app con su nombre, su uso de hoy y su cambio pendiente, por orden alfabetico`() = runTest {
        val limits = FakeAppLimits(listOf(limit(video, 60, FrictionLevel.FIRM), limit(social, 30, FrictionLevel.FLEXIBLE)))
        val pending = FakePendingLimitChanges(listOf(PendingLimitChange(social, limit(social, 90, FrictionLevel.FLEXIBLE), midnight)))
        val vm = blocks(limits, pending)
        observe(vm.uiState)

        val items = vm.uiState.value.items

        assertEquals(listOf("Redes", "Vídeo"), items.map { it.label })
        assertEquals(12 * minute, items[0].usedMillis)
        assertEquals(0L, items[1].usedMillis)
        assertEquals(90, items[0].pending?.newLimit?.dailyMinutesAllowed)
        assertNull(items[1].pending)
        assertFalse(vm.uiState.value.loading)
    }

    @Test
    fun `una app desinstalada se muestra con su nombre de paquete en vez de desaparecer`() = runTest {
        val vm = blocks(FakeAppLimits(listOf(limit("com.example.borrada", 30, FrictionLevel.FIRM))))
        observe(vm.uiState)

        assertEquals("com.example.borrada", vm.uiState.value.items.single().label)
    }

    @Test
    fun `sin limites la lista esta vacia y deja de cargar`() = runTest {
        val vm = blocks(FakeAppLimits())
        observe(vm.uiState)

        assertTrue(vm.uiState.value.items.isEmpty())
        assertFalse(vm.uiState.value.loading)
    }

    @Test
    fun `el estado de la proteccion se refleja en la lista`() = runTest {
        val status = BlockerStatus().also { it.setRunning(true) }
        val vm = blocks(FakeAppLimits(), status = status)
        observe(vm.uiState)
        assertTrue(vm.uiState.value.protectionRunning)

        status.setRunning(false)
        assertFalse(vm.uiState.value.protectionRunning)
    }

    @Test
    fun `al abrir la pantalla se aplican los cambios que ya habian llegado a su hora`() = runTest {
        val limits = FakeAppLimits(listOf(limit(social, 30, FrictionLevel.FIRM)))
        val pending = FakePendingLimitChanges(listOf(PendingLimitChange(social, limit(social, 60, FrictionLevel.FIRM), TEST_CLOCK.instant())))
        val vm = blocks(limits, pending)
        observe(vm.uiState)

        vm.refresh()

        assertEquals(60, limits.items.getValue(social).dailyMinutesAllowed)
        assertEquals(60, vm.uiState.value.items.single().limit.dailyMinutesAllowed)
        assertNull(vm.uiState.value.items.single().pending)
    }

    // ---- Selector de apps

    private fun picker(
        limits: FakeAppLimits = FakeAppLimits(),
        used: FakeUsage = usage(social to 40 * minute, video to 5 * minute),
        device: DeviceState = FakeDeviceState(safe = setOf(dialer)),
    ) = AppPickerViewModel(limits, apps, used, device, own)

    @Test
    fun `el selector ordena por uso de hoy y deja fuera la propia app y el telefono`() = runTest {
        val vm = picker()
        observe(vm.uiState)

        val labels = vm.uiState.value.apps.map { it.app.label }

        assertEquals(listOf("Redes", "Vídeo", "Cámara"), labels)
        assertFalse(vm.uiState.value.loading)
    }

    @Test
    fun `el selector marca las apps que ya tienen limite`() = runTest {
        val vm = picker(limits = FakeAppLimits(listOf(limit(video, 30, FrictionLevel.FIRM))))
        observe(vm.uiState)

        assertEquals(mapOf("Redes" to false, "Vídeo" to true, "Cámara" to false), vm.uiState.value.apps.associate { it.app.label to it.hasLimit })
    }

    @Test
    fun `la busqueda ignora mayusculas y acentos`() = runTest {
        val vm = picker()
        observe(vm.uiState)

        vm.setQuery("CAMARA")
        assertEquals(listOf("Cámara"), vm.uiState.value.apps.map { it.app.label })

        vm.setQuery("  video ")
        assertEquals(listOf("Vídeo"), vm.uiState.value.apps.map { it.app.label })

        vm.setQuery("zzz")
        assertTrue(vm.uiState.value.apps.isEmpty())
        assertFalse(vm.uiState.value.loading)

        vm.setQuery("")
        assertEquals(3, vm.uiState.value.apps.size)
    }

    @Test
    fun `la normalizacion quita acentos y pasa a minusculas`() {
        assertEquals("camara", "Cámara".normalizedForSearch())
        assertEquals("nino", "  Niño ".normalizedForSearch())
    }

    // ---- Editor de límite

    private fun editor(
        pkg: String = social,
        limits: FakeAppLimits = FakeAppLimits(),
        pending: FakePendingLimitChanges = FakePendingLimitChanges(),
    ) = LimitEditorViewModel(
        savedStateHandle = SavedStateHandle(mapOf(LimitEditorViewModel.ARG_PACKAGE_NAME to pkg)),
        limits = limits,
        pendingChanges = pending,
        installedApps = apps,
        usage = usage(social to 12 * minute),
        saveLimit = SaveAppLimitUseCase(limits, pending, TEST_CLOCK),
        cancelPending = CancelPendingLimitChangeUseCase(pending),
        clock = TEST_CLOCK,
    )

    private suspend fun LimitEditorViewModel.closed() = withTimeoutOrNull(100) { events.first() } == LimitEditorEvent.Close

    @Test
    fun `una app sin limite arranca con 30 minutos flexibles y se guarda al momento`() = runTest {
        val limits = FakeAppLimits()
        val vm = editor(limits = limits)

        val state = vm.uiState.value
        assertEquals(30, state.minutes)
        assertEquals(FrictionLevel.FLEXIBLE, state.friction)
        assertFalse(state.hasLimit)
        assertEquals("Redes", state.label)
        assertEquals(12 * minute, state.usedMillis)
        assertEquals(ChangeTiming.NEW, state.timing)

        vm.save()

        assertTrue(vm.closed())
        assertEquals(limit(social, 30, FrictionLevel.FLEXIBLE), limits.items[social])
    }

    @Test
    fun `al editar se carga el limite actual`() = runTest {
        val vm = editor(limits = FakeAppLimits(listOf(limit(social, 45, FrictionLevel.FIRM))))

        val state = vm.uiState.value
        assertEquals(45, state.minutes)
        assertEquals(FrictionLevel.FIRM, state.friction)
        assertTrue(state.hasLimit)
        assertEquals(ChangeTiming.UNCHANGED, state.timing)
    }

    @Test
    fun `el aviso de cuando entra en vigor sigue a lo que se edita`() = runTest {
        val vm = editor(limits = FakeAppLimits(listOf(limit(social, 60, FrictionLevel.FLEXIBLE))))

        vm.setMinutes(30)
        assertEquals(ChangeTiming.NOW, vm.uiState.value.timing)

        vm.setMinutes(90)
        assertEquals(ChangeTiming.TOMORROW, vm.uiState.value.timing)

        vm.setMinutes(60)
        assertEquals(ChangeTiming.UNCHANGED, vm.uiState.value.timing)

        vm.setFriction(FrictionLevel.CLOSED)
        assertEquals(ChangeTiming.NOW, vm.uiState.value.timing)

        // Menos ampliaciones y menos minutos: todo endurece, así que rige ya.
        vm.setFriction(FrictionLevel.FIRM)
        vm.setMinutes(30)
        assertEquals(ChangeTiming.NOW, vm.uiState.value.timing)
    }

    @Test
    fun `un cambio mixto se anuncia como parcial`() = runTest {
        val vm = editor(limits = FakeAppLimits(listOf(limit(social, 60, FrictionLevel.FIRM))))

        vm.setMinutes(30)
        vm.setFriction(FrictionLevel.FLEXIBLE)

        assertEquals(ChangeTiming.PARTIAL, vm.uiState.value.timing)
    }

    @Test
    fun `al editar se carga si la app es de ocio, y una nueva no lo es`() = runTest {
        assertFalse(editor().uiState.value.leisure)
        assertFalse(editor(limits = FakeAppLimits(listOf(limit(social, 30, FrictionLevel.FIRM)))).uiState.value.leisure)
        assertTrue(editor(limits = FakeAppLimits(listOf(limit(social, 30, FrictionLevel.FIRM).copy(isLeisure = true)))).uiState.value.leisure)
    }

    @Test
    fun `marcar la app como de ocio se anuncia para medianoche y se guarda como cambio pendiente`() = runTest {
        val limits = FakeAppLimits(listOf(limit(social, 30, FrictionLevel.FIRM)))
        val pending = FakePendingLimitChanges()
        val vm = editor(limits = limits, pending = pending)

        vm.setLeisure(true)
        assertEquals(ChangeTiming.TOMORROW, vm.uiState.value.timing)
        vm.save()

        assertTrue(vm.closed())
        assertFalse(limits.items.getValue(social).isLeisure)
        assertTrue(pending.items.getValue(social).newLimit?.isLeisure == true)
    }

    @Test
    fun `desmarcar la app de ocio rige al momento`() = runTest {
        val limits = FakeAppLimits(listOf(limit(social, 30, FrictionLevel.FIRM).copy(isLeisure = true)))
        val vm = editor(limits = limits)

        vm.setLeisure(false)
        assertEquals(ChangeTiming.NOW, vm.uiState.value.timing)
        vm.save()

        assertTrue(vm.closed())
        assertFalse(limits.items.getValue(social).isLeisure)
    }

    @Test
    fun `una app nueva con la marca de ocio se crea con la marca`() = runTest {
        val limits = FakeAppLimits()
        val vm = editor(limits = limits)

        vm.setLeisure(true)
        vm.save()

        assertTrue(vm.closed())
        assertTrue(limits.items.getValue(social).isLeisure)
    }

    @Test
    fun `relajar guarda un cambio pendiente y no toca el limite`() = runTest {
        val limits = FakeAppLimits(listOf(limit(social, 30, FrictionLevel.FIRM)))
        val pending = FakePendingLimitChanges()
        val vm = editor(limits = limits, pending = pending)

        vm.setMinutes(120)
        vm.save()

        assertTrue(vm.closed())
        assertEquals(30, limits.items.getValue(social).dailyMinutesAllowed)
        assertEquals(120, pending.items.getValue(social).newLimit?.dailyMinutesAllowed)
    }

    @Test
    fun `los minutos se quedan entre 5 y 480`() = runTest {
        val vm = editor()

        vm.setMinutes(1)
        assertEquals(5, vm.uiState.value.minutes)
        vm.setMinutes(10_000)
        assertEquals(480, vm.uiState.value.minutes)
    }

    @Test
    fun `quitar el limite lo borra en el acto y cierra el editor`() = runTest {
        val limits = FakeAppLimits(listOf(limit(social, 30, FrictionLevel.FLEXIBLE)))
        val pending = FakePendingLimitChanges(listOf(PendingLimitChange(social, limit(social, 90, FrictionLevel.FLEXIBLE), midnight)))
        val vm = editor(limits = limits, pending = pending)

        vm.remove()

        assertTrue(vm.closed())
        assertNull(limits.items[social])
        assertTrue(pending.items.isEmpty())
    }

    @Test
    fun `con un cambio pendiente se parte de lo que se habia decidido y se puede cancelar`() = runTest {
        val limits = FakeAppLimits(listOf(limit(social, 30, FrictionLevel.FIRM)))
        val pending = FakePendingLimitChanges(listOf(PendingLimitChange(social, limit(social, 90, FrictionLevel.FLEXIBLE), midnight)))
        val vm = editor(limits = limits, pending = pending)

        assertEquals(90, vm.uiState.value.minutes)
        assertEquals(FrictionLevel.FLEXIBLE, vm.uiState.value.friction)
        assertEquals(ChangeTiming.TOMORROW, vm.uiState.value.timing)
        assertEquals(90, vm.uiState.value.pending?.newLimit?.dailyMinutesAllowed)

        vm.cancelPendingChange()

        assertTrue(pending.items.isEmpty())
        assertNull(vm.uiState.value.pending)
        assertEquals(30, vm.uiState.value.minutes)
        assertEquals(FrictionLevel.FIRM, vm.uiState.value.friction)
        assertEquals(ChangeTiming.UNCHANGED, vm.uiState.value.timing)
    }

    @Test
    fun `si el cambio pendiente es quitar, el editor muestra el limite actual`() = runTest {
        val limits = FakeAppLimits(listOf(limit(social, 45, FrictionLevel.FIRM)))
        val pending = FakePendingLimitChanges(listOf(PendingLimitChange(social, null, midnight)))
        val vm = editor(limits = limits, pending = pending)

        assertEquals(45, vm.uiState.value.minutes)
        assertEquals(FrictionLevel.FIRM, vm.uiState.value.friction)
        assertTrue(vm.uiState.value.pending!!.isRemoval)
    }

    @Test
    fun `una app sin nombre conocido se muestra con su paquete`() = runTest {
        assertEquals("com.example.rara", editor(pkg = "com.example.rara").uiState.value.label)
    }

    @Test
    fun `el limite por defecto de un limite nuevo es el nivel flexible`() {
        assertEquals(AppLimit.DEFAULT_EXTENSIONS_PER_DAY, appLimitOf(social, 30, FrictionLevel.FLEXIBLE).extensionsPerDay)
    }
}
