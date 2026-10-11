package com.focusdivergent.app.presentation.closure

import android.app.Application
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.focusdivergent.app.domain.closure.DayClosureConfig
import com.focusdivergent.app.domain.closure.DaySummary
import com.focusdivergent.app.domain.closure.PendingClosureConfig
import com.focusdivergent.app.presentation.common.formatShort
import com.focusdivergent.app.presentation.theme.FocusDivergentTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** El resumen del Cierre y sus ajustes, dibujados con un estado fijo. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "es-rES-w411dp-h2000dp")
class ClosureScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val day = LocalDate.of(2026, 10, 10)

    private fun summary(
        titles: List<String> = listOf("Informe", "Compra"),
        tasks: Int = titles.size,
        routines: Int = 0,
        focus: Int = 0,
        xp: Int = 0,
        coins: Int = 0,
        moved: Int = 0,
        respected: Int = 0,
        ideas: Int = 0,
    ) = DaySummary(day, titles, tasks, routines, focus, xp, coins, moved, respected, ideas)

    // ---- Resumen

    private fun showSummary(
        state: ClosureSummaryUiState,
        onClose: () -> Unit = {},
        onReviewInbox: () -> Unit = {},
    ) = compose.setContent {
        FocusDivergentTheme { ClosureSummaryContent(state, onClose, onReviewInbox) }
    }

    @Test
    fun `el resumen enseña lo hecho con sus titulos y el dia`() {
        showSummary(ClosureSummaryUiState(summary(titles = listOf("Informe", "Compra", "Medicación"), tasks = 2, routines = 1)))

        compose.onNodeWithText("3 cosas hechas").assertExists()
        compose.onNodeWithText("Informe").assertExists()
        compose.onNodeWithText("Compra").assertExists()
        compose.onNodeWithText("Medicación").assertExists()
        compose.onNodeWithText("Sábado, 10 de octubre de 2026").assertExists()
    }

    @Test
    fun `una sola cosa hecha va en singular`() {
        showSummary(ClosureSummaryUiState(summary(titles = listOf("Informe"))))

        compose.onNodeWithText("1 cosa hecha").assertExists()
    }

    @Test
    fun `si hay muchas, enseña las primeras y resume el resto`() {
        showSummary(ClosureSummaryUiState(summary(titles = (1..9).map { "Cosa $it" })))

        compose.onNodeWithText("Cosa 6").assertExists()
        compose.onNodeWithText("Cosa 7").assertDoesNotExist()
        compose.onNodeWithText("y 3 más").assertExists()
    }

    @Test
    fun `un dia sin nada marcado se dice con calma y no enseña ningun numero`() {
        showSummary(ClosureSummaryUiState(summary(titles = emptyList())))

        compose.onNodeWithText("Hoy no quedó nada marcado, y no pasa nada. Mañana empieza de nuevo.").assertExists()
        compose.onNodeWithText("cosas hechas", substring = true).assertDoesNotExist()
        compose.onNodeWithText("minutos de enfoque", substring = true).assertDoesNotExist()
    }

    @Test
    fun `si solo hubo enfoque lo cuenta`() {
        showSummary(ClosureSummaryUiState(summary(titles = emptyList(), focus = 45)))

        compose.onNodeWithText("Hoy tu tiempo de enfoque fue lo que contó.").assertExists()
        compose.onNodeWithText("45 minutos de enfoque").assertExists()
    }

    @Test
    fun `enseña el enfoque, el XP y las monedas del dia`() {
        showSummary(ClosureSummaryUiState(summary(focus = 1, xp = 35, coins = 3)))

        compose.onNodeWithText("1 minuto de enfoque").assertExists()
        compose.onNodeWithText("+35 XP y +3 monedas de enfoque").assertExists()
    }

    @Test
    fun `sin monedas solo cuenta el XP, y una moneda va en singular`() {
        showSummary(ClosureSummaryUiState(summary(xp = 10)))
        compose.onNodeWithText("+10 XP").assertExists()
    }

    @Test
    fun `una moneda va en singular`() {
        showSummary(ClosureSummaryUiState(summary(xp = 10, coins = 1)))
        compose.onNodeWithText("+10 XP y +1 moneda de enfoque").assertExists()
    }

    @Test
    fun `enseña lo trasladado sin ningun reproche`() {
        showSummary(ClosureSummaryUiState(summary(moved = 3)))

        compose.onNodeWithText("3 tareas pasan a mañana").assertExists()
        compose.onNodeWithText("Ya están en su sitio: no hay que hacer nada.").assertExists()
    }

    @Test
    fun `una tarea trasladada va en singular y nada trasladado no enseña la tarjeta`() {
        showSummary(ClosureSummaryUiState(summary(moved = 1)))
        compose.onNodeWithText("1 tarea pasa a mañana").assertExists()
    }

    @Test
    fun `sin tareas trasladadas ni limites respetados no salen esas tarjetas`() {
        showSummary(ClosureSummaryUiState(summary()))

        compose.onNodeWithText("pasan a mañana", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Respetaste", substring = true).assertDoesNotExist()
    }

    @Test
    fun `enseña los limites que se respetaron`() {
        showSummary(ClosureSummaryUiState(summary(respected = 2)))

        compose.onNodeWithText("Respetaste el límite de 2 apps").assertExists()
    }

    @Test
    fun `invita a ordenar la bandeja si hay ideas y lleva a ella`() {
        var reviewed = 0
        showSummary(ClosureSummaryUiState(summary(ideas = 4)), onReviewInbox = { reviewed++ })

        compose.onNodeWithText("Tienes 4 ideas esperando en la bandeja").assertExists()
        compose.onNodeWithText("Revisar la bandeja").performScrollTo().performClick()

        assertEquals(1, reviewed)
    }

    @Test
    fun `sin ideas no hay invitacion`() {
        showSummary(ClosureSummaryUiState(summary(ideas = 0)))

        compose.onNodeWithText("Revisar la bandeja").assertDoesNotExist()
    }

    @Test
    fun `avisa de hasta cuando quedan libres las apps de ocio`() {
        showSummary(ClosureSummaryUiState(summary(), leisureApps = 2, liftUntil = LocalTime.of(6, 0)))

        compose.onNodeWithText("Hasta las ${LocalTime.of(6, 0).formatShort()}, 2 apps de ocio quedan libres.").assertExists()
    }

    @Test
    fun `sin apps de ocio o con el cierre desactivado no dice nada de apps libres`() {
        showSummary(ClosureSummaryUiState(summary(), leisureApps = 0, liftUntil = LocalTime.of(6, 0)))
        compose.onNodeWithText("de ocio", substring = true).assertDoesNotExist()
    }

    @Test
    fun `Listo cierra el resumen`() {
        var closed = 0
        showSummary(ClosureSummaryUiState(summary()), onClose = { closed++ })

        compose.onNodeWithText("Listo").performClick()

        assertEquals(1, closed)
    }

    @Test
    fun `mientras no hay datos solo se ve el boton`() {
        showSummary(ClosureSummaryUiState(summary = null))

        compose.onNodeWithText("Listo").assertExists()
        compose.onNodeWithText("Lo que hiciste").assertDoesNotExist()
    }

    // ---- Ajustes

    private fun showSettings(
        state: ClosureSettingsUiState,
        onEnabled: (Boolean) -> Unit = {},
        onCloseAt: (LocalTime) -> Unit = {},
        onLiftUntil: (LocalTime) -> Unit = {},
        onCancelPending: () -> Unit = {},
        onOpenSummary: () -> Unit = {},
    ) = compose.setContent {
        FocusDivergentTheme {
            ClosureSettingsContent(state, {}, onEnabled, onCloseAt, onLiftUntil, onCancelPending, onOpenSummary)
        }
    }

    private fun time(h: Int, m: Int = 0) = LocalTime.of(h, m).formatShort()

    @Test
    fun `los ajustes enseñan el interruptor y las horas elegidas`() {
        showSettings(ClosureSettingsUiState())

        compose.onNodeWithText("Cerrar el día automáticamente").assertExists()
        compose.onNodeWithText(time(21, 30)).assertIsSelected()
        compose.onNodeWithText(time(6)).assertIsSelected()
        compose.onNodeWithText(time(22)).assertExists()
        compose.onNodeWithText(time(8)).assertExists()
    }

    @Test
    fun `desactivado no ofrece elegir horas`() {
        showSettings(ClosureSettingsUiState(effective = DayClosureConfig(enabled = false)))

        compose.onNodeWithText("Hora del cierre").assertDoesNotExist()
        compose.onNodeWithText("Apps de ocio libres hasta").assertDoesNotExist()
    }

    @Test
    fun `tocar el interruptor avisa del nuevo valor`() {
        val values = mutableListOf<Boolean>()
        showSettings(ClosureSettingsUiState(), onEnabled = { values += it })

        compose.onNodeWithText("Cerrar el día automáticamente").performClick()

        assertEquals(listOf(false), values)
    }

    @Test
    fun `elegir una hora de cierre y una de la mañana avisa de cual`() {
        val closes = mutableListOf<LocalTime>()
        val lifts = mutableListOf<LocalTime>()
        showSettings(ClosureSettingsUiState(), onCloseAt = { closes += it }, onLiftUntil = { lifts += it })

        compose.onNodeWithText(time(22, 30)).performClick()
        compose.onNodeWithText(time(7)).performClick()

        assertEquals(listOf(LocalTime.of(22, 30)), closes)
        assertEquals(listOf(LocalTime.of(7, 0)), lifts)
    }

    @Test
    fun `con un cambio pendiente enseña lo elegido y lo avisa para medianoche, con opcion de cancelarlo`() {
        var cancelled = 0
        val pending = PendingClosureConfig(DayClosureConfig(closeAt = LocalTime.of(20, 0), liftUntil = LocalTime.of(8, 0)), Instant.parse("2026-10-11T00:00:00Z"))
        showSettings(ClosureSettingsUiState(pending = pending), onCancelPending = { cancelled++ })

        compose.onNodeWithText("Cambio para esta medianoche").assertExists()
        compose.onNodeWithText("Cierre a las ${time(20)}, con las apps de ocio libres hasta las ${time(8)}.").assertExists()
        compose.onNodeWithText(time(20)).assertIsSelected()
        compose.onNodeWithText("Cancelar el cambio").performClick()

        assertEquals(1, cancelled)
    }

    @Test
    fun `un cambio pendiente que desactiva el cierre lo dice`() {
        val pending = PendingClosureConfig(DayClosureConfig(enabled = false), Instant.parse("2026-10-11T00:00:00Z"))
        showSettings(ClosureSettingsUiState(pending = pending))

        compose.onNodeWithText("Cierre desactivado.").assertExists()
    }

    @Test
    fun `sin cambio pendiente no sale el aviso`() {
        showSettings(ClosureSettingsUiState())

        compose.onNodeWithText("Cambio para esta medianoche").assertDoesNotExist()
    }

    @Test
    fun `explica que pasa al cerrar y que el Reality Check y las Obligaciones siguen`() {
        showSettings(ClosureSettingsUiState())

        compose.onNodeWithText("Qué pasa al cerrar").performScrollTo().assertExists()
        compose.onNodeWithText("Las tareas con fecha de hoy", substring = true).performScrollTo().assertExists()
        compose.onNodeWithText("El Reality Check y los avisos de Obligaciones siguen funcionando.", substring = true).performScrollTo().assertExists()
    }

    @Test
    fun `cuenta las apps de ocio marcadas`() {
        showSettings(ClosureSettingsUiState(leisureApps = 1))
        compose.onNodeWithText("1 app marcada como de ocio.", substring = true).performScrollTo().assertExists()
    }

    @Test
    fun `desde los ajustes se abre el resumen de hoy`() {
        var opened = 0
        showSettings(ClosureSettingsUiState(), onOpenSummary = { opened++ })

        compose.onNodeWithText("Resumen de hoy").performScrollTo().performClick()

        assertEquals(1, opened)
    }
}
