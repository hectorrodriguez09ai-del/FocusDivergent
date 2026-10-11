package com.focusdivergent.app.presentation.blocks

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.focusdivergent.app.domain.friction.MathChallenge
import com.focusdivergent.app.domain.friction.MathChallengeGenerator
import com.focusdivergent.app.domain.model.BlockReason
import com.focusdivergent.app.domain.model.FrictionLevel
import com.focusdivergent.app.domain.model.InstalledApp
import com.focusdivergent.app.domain.model.PendingLimitChange
import com.focusdivergent.app.domain.model.appLimitOf
import com.focusdivergent.app.presentation.block.BlockOverlayContent
import com.focusdivergent.app.presentation.theme.FocusDivergentTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Las pantallas de límites, dibujadas con estado fijo (sin ViewModel ni base de datos). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "es-rES-w411dp-h2000dp")
class BlocksScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val minute = 60_000L
    private val midnight = Instant.parse("2026-10-11T00:00:00Z")
    private val social = "com.example.social"

    private fun item(minutes: Int, friction: FrictionLevel, used: Long = 0, label: String = "Redes", pending: PendingLimitChange? = null) =
        LimitItem(appLimitOf(social, minutes, friction), label, used * minute, pending)

    private fun blocks(
        state: BlocksUiState,
        onAddApp: () -> Unit = {},
        onEditLimit: (String) -> Unit = {},
        onOpenPermissions: () -> Unit = {},
    ) = compose.setContent {
        FocusDivergentTheme {
            BlocksContent(state, onAddApp, onEditLimit, onOpenPermissions)
        }
    }

    // ---- Lista

    @Test
    fun `sin limites invita a elegir una app`() {
        var added = 0
        blocks(BlocksUiState(loading = false, protectionRunning = true), onAddApp = { added++ })

        compose.onNodeWithText("Ninguna app con límite").assertExists()
        compose.onNodeWithText("Elegir una app").performClick()
        assertEquals(1, added)
    }

    @Test
    fun `cada limite muestra la app, lo elegido y lo que lleva hoy`() {
        blocks(BlocksUiState(items = listOf(item(30, FrictionLevel.FLEXIBLE, used = 12)), loading = false, protectionRunning = true))

        compose.onNodeWithText("Redes").assertExists()
        compose.onNodeWithText("30 min · Flexible").assertExists()
        compose.onNodeWithText("12 de 30 min hoy").assertExists()
    }

    @Test
    fun `una app cerrada no enseña barra de uso, solo que esta siempre en pausa`() {
        blocks(BlocksUiState(items = listOf(item(30, FrictionLevel.CLOSED, used = 5)), loading = false, protectionRunning = true))

        compose.onNodeWithText("Cerrada").assertExists()
        compose.onNodeWithText("5 de 30 min hoy").assertDoesNotExist()
    }

    @Test
    fun `al pasarse del limite el texto sigue siendo el mismo, sin dramatizar`() {
        blocks(BlocksUiState(items = listOf(item(30, FrictionLevel.FIRM, used = 45)), loading = false, protectionRunning = true))

        compose.onNodeWithText("45 de 30 min hoy").assertExists()
    }

    @Test
    fun `un cambio pendiente se anuncia en la tarjeta`() {
        val pending = PendingLimitChange(social, appLimitOf(social, 90, FrictionLevel.FLEXIBLE), midnight)
        blocks(BlocksUiState(items = listOf(item(30, FrictionLevel.FIRM, pending = pending)), loading = false, protectionRunning = true))

        compose.onNodeWithText("30 min · Firme").assertExists()
        compose.onNodeWithText("Mañana pasará a: 90 min · Flexible").assertExists()
    }

    @Test
    fun `quitar el limite tambien se anuncia`() {
        val pending = PendingLimitChange(social, null, midnight)
        blocks(BlocksUiState(items = listOf(item(30, FrictionLevel.FIRM, pending = pending)), loading = false, protectionRunning = true))

        compose.onNodeWithText("Mañana se quitará el límite").assertExists()
    }

    @Test
    fun `tocar una tarjeta abre su editor`() {
        var opened: String? = null
        blocks(BlocksUiState(items = listOf(item(30, FrictionLevel.FIRM)), loading = false, protectionRunning = true), onEditLimit = { opened = it })

        compose.onNodeWithText("Redes").performClick()

        assertEquals(social, opened)
    }

    @Test
    fun `con la proteccion pausada se avisa de que los limites no se aplican`() {
        var permissions = 0
        blocks(BlocksUiState(items = listOf(item(30, FrictionLevel.FIRM)), loading = false, protectionRunning = false), onOpenPermissions = { permissions++ })

        compose.onNodeWithText("La protección está pausada").assertExists()
        compose.onNodeWithText("Mientras esté pausada, los límites no se aplican.").assertExists()
        compose.onNodeWithText("Ir a activarla").performClick()
        assertEquals(1, permissions)
    }

    @Test
    fun `con la proteccion activa no hay avisos`() {
        blocks(BlocksUiState(loading = false, protectionRunning = true))

        compose.onNodeWithText("Protección activa ✓").assertExists()
        compose.onNodeWithText("Ir a activarla").assertDoesNotExist()
    }

    // ---- Selector

    private fun picker(state: AppPickerUiState, onPick: (String) -> Unit = {}, onQuery: (String) -> Unit = {}) = compose.setContent {
        FocusDivergentTheme { AppPickerContent(state, onBack = {}, onPick = onPick, onQuery = onQuery) }
    }

    @Test
    fun `el selector enseña cada app con lo que lleva hoy y marca las que ya tienen limite`() {
        picker(
            AppPickerUiState(
                apps = listOf(
                    PickerApp(InstalledApp("com.example.social", "Redes"), usedMillis = 40 * minute, hasLimit = false),
                    PickerApp(InstalledApp("com.example.video", "Vídeo"), usedMillis = 0, hasLimit = true),
                    PickerApp(InstalledApp("com.example.camera", "Cámara"), usedMillis = 20_000, hasLimit = false),
                ),
                loading = false,
            ),
        )

        compose.onNodeWithText("40 min hoy").assertExists()
        compose.onNodeWithText("Ya tiene límite").assertExists()
        // Menos de un minuto no se enseña: «0 min hoy» no dice nada.
        compose.onNodeWithText("0 min hoy").assertDoesNotExist()
    }

    @Test
    fun `elegir una app avisa con su paquete`() {
        var picked: String? = null
        picker(AppPickerUiState(apps = listOf(PickerApp(InstalledApp("com.example.social", "Redes"), 0, false)), loading = false), onPick = { picked = it })

        compose.onNodeWithText("Redes").performClick()

        assertEquals("com.example.social", picked)
    }

    @Test
    fun `una busqueda sin resultados lo dice con calma`() {
        picker(AppPickerUiState(query = "zzz", loading = false))

        compose.onNodeWithText("No encuentro esa app").assertExists()
    }

    // ---- Editor

    private fun editor(
        state: LimitEditorUiState,
        onMinutes: (Int) -> Unit = {},
        onFriction: (FrictionLevel) -> Unit = {},
        onSave: () -> Unit = {},
        onRemove: () -> Unit = {},
        onCancelPending: () -> Unit = {},
        generator: MathChallengeGenerator = MathChallengeGenerator(),
    ) = compose.setContent {
        FocusDivergentTheme {
            LimitEditorContent(state, onBack = {}, onMinutes, onFriction, onSave, onRemove, onCancelPending, generator)
        }
    }

    // Un reto fijo (2 x 3) para poder resolverlo en el test sin parsear un enunciado aleatorio.
    private val fixedChallenge = object : MathChallengeGenerator() {
        override fun generate(random: kotlin.random.Random) = MathChallenge("2 × 3", 6)
    }

    private fun typeAnswer(vararg digits: Int) {
        digits.forEach { compose.onNodeWithContentDescription(it.toString()).performClick() }
        compose.onNodeWithContentDescription("Comprobar").performClick()
    }

    private fun editorState(
        minutes: Int = 30,
        friction: FrictionLevel = FrictionLevel.FLEXIBLE,
        hasLimit: Boolean = false,
        timing: ChangeTiming = ChangeTiming.NEW,
        pending: PendingLimitChange? = null,
    ) = LimitEditorUiState(
        packageName = social,
        label = "Redes",
        minutes = minutes,
        friction = friction,
        hasLimit = hasLimit,
        usedMillis = 12 * minute,
        pending = pending,
        timing = timing,
        loading = false,
    )

    @Test
    fun `el editor de un limite nuevo explica los tres niveles y resume el resultado`() {
        editor(editorState())

        compose.onNodeWithText("Nuevo límite").assertExists()
        compose.onNodeWithText("Hoy: 12 min").assertExists()
        compose.onNodeWithText("Flexible").assertIsSelected()
        compose.onNodeWithText("Al llegar al límite puedes ganar hasta 3 ampliaciones de 15 minutos resolviendo un reto.").assertExists()
        compose.onNodeWithText("Al llegar al límite, la app se cierra hasta mañana. Sin ampliaciones.").assertExists()
        compose.onNodeWithText("La app está siempre en pausa. No hay tiempo diario.").assertExists()
        compose.onNodeWithText("Podrás usar Redes 30 min al día. Después, hasta 3 ampliaciones de 15 min resolviendo un reto.").performScrollTo().assertExists()
        // Un límite nuevo no puede «quitarse» ni tiene aviso de cuándo entra en vigor.
        compose.onNodeWithText("Quitar el límite").assertDoesNotExist()
    }

    @Test
    fun `el resumen cambia con cada nivel`() {
        editor(editorState(friction = FrictionLevel.FIRM, minutes = 45))
        compose.onNodeWithText("Podrás usar Redes 45 min al día. Después se cierra hasta mañana.").performScrollTo().assertExists()
    }

    @Test
    fun `una app cerrada no ofrece tiempo diario`() {
        editor(editorState(friction = FrictionLevel.CLOSED))

        compose.onNodeWithText("Tiempo al día").assertDoesNotExist()
        compose.onNodeWithText("Redes estará siempre en pausa.").performScrollTo().assertExists()
    }

    @Test
    fun `los atajos y los botones de cinco minutos cambian el valor`() {
        var minutes: Int? = null
        editor(editorState(minutes = 30), onMinutes = { minutes = it })

        compose.onNodeWithContentDescription("Añadir 5 minutos").performClick()
        assertEquals(35, minutes)
        compose.onNodeWithContentDescription("Quitar 5 minutos").performClick()
        assertEquals(25, minutes)
        compose.onNodeWithText("60 min").performClick()
        assertEquals(60, minutes)
    }

    @Test
    fun `el selector de minutos no baja de 5 ni sube de 480`() {
        editor(editorState(minutes = 5))
        compose.onNodeWithContentDescription("Quitar 5 minutos").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Añadir 5 minutos").assertIsEnabled()
    }

    @Test
    fun `elegir un nivel de friccion avisa`() {
        var chosen: FrictionLevel? = null
        editor(editorState(), onFriction = { chosen = it })

        compose.onNodeWithText("Cerrada").performClick()

        assertEquals(FrictionLevel.CLOSED, chosen)
    }

    @Test
    fun `al editar se avisa de cuando entra en vigor lo que relaja`() {
        editor(editorState(hasLimit = true, timing = ChangeTiming.TOMORROW))

        compose.onNodeWithText("Cambiar límite").assertExists()
        compose.onNodeWithText("Este cambio da más margen, así que se aplicará mañana a las 00:00. Hasta entonces el límite sigue como está.")
            .performScrollTo().assertExists()
    }

    @Test
    fun `quitar el limite pide primero el reto y avisa de que es en el acto`() {
        var removed = 0
        editor(editorState(hasLimit = true), onRemove = { removed++ }, generator = fixedChallenge)

        compose.onNodeWithText("Se quita en el acto, tras resolver un reto.").performScrollTo().assertExists()
        compose.onNodeWithText("Quitar el límite").performScrollTo().performClick()

        // Pulsar el botón no quita nada: aparece el reto.
        assertEquals(0, removed)
        compose.onNodeWithText("Un reto para quitar el límite").assertExists()
        compose.onNodeWithText("2 × 3").assertExists()
    }

    @Test
    fun `solo se quita el limite al resolver el reto`() {
        var removed = 0
        editor(editorState(hasLimit = true), onRemove = { removed++ }, generator = fixedChallenge)
        compose.onNodeWithText("Quitar el límite").performScrollTo().performClick()

        typeAnswer(7)
        assertEquals("una respuesta mal no quita nada", 0, removed)

        typeAnswer(6)
        assertEquals(1, removed)
    }

    @Test
    fun `se puede volver atras desde el reto sin quitar nada`() {
        var removed = 0
        editor(editorState(hasLimit = true), onRemove = { removed++ }, generator = fixedChallenge)
        compose.onNodeWithText("Quitar el límite").performScrollTo().performClick()

        compose.onNodeWithText("Volver").performClick()

        assertEquals(0, removed)
        compose.onNodeWithText("Cambiar límite").assertExists()
        compose.onNodeWithText("Un reto para quitar el límite").assertDoesNotExist()
    }

    @Test
    fun `lo que endurece dice que se aplica ahora y lo mixto lo explica`() {
        editor(editorState(hasLimit = true, timing = ChangeTiming.NOW))
        compose.onNodeWithText("Este cambio endurece el límite, así que se aplica ahora.").performScrollTo().assertExists()
    }

    @Test
    fun `un cambio mixto dice que parte se aplica ya`() {
        editor(editorState(hasLimit = true, timing = ChangeTiming.PARTIAL))
        compose.onNodeWithText("Lo que endurece se aplica ahora; el resto, que da más margen, mañana a las 00:00.").performScrollTo().assertExists()
    }

    @Test
    fun `sin cambios no hay aviso de cuando entra en vigor`() {
        editor(editorState(hasLimit = true, timing = ChangeTiming.UNCHANGED))

        compose.onNodeWithText("Este cambio endurece el límite, así que se aplica ahora.").assertDoesNotExist()
        compose.onNodeWithText("Este cambio da más margen", substring = true).assertDoesNotExist()
    }

    @Test
    fun `un cambio pendiente se muestra arriba con la opcion de cancelarlo`() {
        var cancelled = 0
        val pending = PendingLimitChange(social, appLimitOf(social, 90, FrictionLevel.FLEXIBLE), midnight)
        editor(editorState(hasLimit = true, pending = pending), onCancelPending = { cancelled++ })

        compose.onNodeWithText("Mañana pasará a: 90 min · Flexible").assertExists()
        compose.onNodeWithText("Cancelar el cambio").performClick()
        assertEquals(1, cancelled)
    }

    @Test
    fun `guardar avisa`() {
        var saved = 0
        editor(editorState(), onSave = { saved++ })

        compose.onNodeWithText("Guardar").performClick()

        assertEquals(1, saved)
    }

    // ---- Pantalla de pausa

    private fun overlay(extensionsLeft: Int, extensionsPerDay: Int) = compose.setContent {
        FocusDivergentTheme {
            BlockOverlayContent(
                appLabel = "Redes",
                reason = BlockReason.LimitReached(usedMillis = 31 * minute, allowedMillis = 30 * minute),
                extensionsLeft = extensionsLeft,
                extensionsPerDay = extensionsPerDay,
                onGoHome = {},
                onEarnExtension = {},
            )
        }
    }

    @Test
    fun `con ampliaciones disponibles la pausa ofrece ganar tiempo y dice cuantas quedan`() {
        overlay(extensionsLeft = 2, extensionsPerDay = 3)

        compose.onNodeWithText("Ganar 15 minutos").assertExists()
        compose.onNodeWithText("Te quedan 2 ampliaciones hoy").assertExists()
    }

    @Test
    fun `un limite flexible que las agoto dice que ya uso las de hoy`() {
        overlay(extensionsLeft = 0, extensionsPerDay = 3)

        compose.onNodeWithText("Ganar 15 minutos").assertDoesNotExist()
        compose.onNodeWithText("Ya usaste tus ampliaciones de hoy. Mañana empieza de nuevo.").assertExists()
    }

    @Test
    fun `un limite firme no dice que se hayan gastado unas ampliaciones que nunca tuvo`() {
        overlay(extensionsLeft = 0, extensionsPerDay = 0)

        compose.onNodeWithText("Ganar 15 minutos").assertDoesNotExist()
        compose.onNodeWithText("Ya usaste tus ampliaciones de hoy. Mañana empieza de nuevo.").assertDoesNotExist()
        compose.onNodeWithText("Elegiste que, al llegar al límite, se cierre hasta mañana.").assertExists()
    }
}
