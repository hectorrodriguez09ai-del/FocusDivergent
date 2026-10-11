package com.focusdivergent.app.presentation.mascot

import android.app.Application
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.focusdivergent.app.domain.reward.Cosmetic
import com.focusdivergent.app.domain.reward.Levels
import com.focusdivergent.app.domain.reward.Mascot
import com.focusdivergent.app.domain.reward.MascotSelection
import com.focusdivergent.app.domain.reward.StreakInfo
import com.focusdivergent.app.domain.usecase.Progress
import com.focusdivergent.app.presentation.theme.FocusDivergentTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** La tarjeta de «Hoy» y la pantalla «Mi mascota», dibujadas con un progreso fijo. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "es-rES-w411dp-h2000dp")
class MascotScreenTest {

    @get:Rule
    val compose = createComposeRule()

    /** Nivel 4 (20 de 70 XP en el nivel), etapa 2, con una planta puesta y el zorro. */
    private fun progress(
        mascot: Mascot = Mascot.FOX,
        streak: StreakInfo = StreakInfo(current = 3, best = 12),
        coins: Int = 12,
        owned: Set<Cosmetic> = setOf(Cosmetic.SCENERY_CALM, Cosmetic.PROP_PLANT, Cosmetic.PROP_BOOKS),
        selection: MascotSelection = MascotSelection(mascot, Cosmetic.SCENERY_CALM, Cosmetic.PROP_PLANT),
    ) = Progress(
        level = Levels.of(170),
        stage = 2,
        xpToday = 35,
        coins = coins,
        streak = streak,
        owned = owned,
        selection = selection,
    )

    // ---- Tarjeta de «Hoy»

    private fun card(progress: Progress, onOpen: () -> Unit = {}) = compose.setContent {
        FocusDivergentTheme { MascotCard(progress = progress, onOpen = onOpen) }
    }

    @Test
    fun `la tarjeta enseña el nivel y la racha sin ningun numero de XP`() {
        card(progress())

        compose.onNodeWithText("Nivel 4").assertExists()
        compose.onNodeWithText("Racha: 3 días").assertExists()
        compose.onNodeWithText("Mejor racha: 12 días").assertExists()
        compose.onNodeWithText("20 de 70 XP en este nivel · 35 XP hoy").assertDoesNotExist()
    }

    @Test
    fun `tocar la barra enseña los numeros y volver a tocarla los esconde`() {
        card(progress())

        compose.onNodeWithContentDescription("Nivel 4").performClick()
        compose.onNodeWithText("20 de 70 XP en este nivel · 35 XP hoy").assertExists()

        compose.onNodeWithContentDescription("Nivel 4").performClick()
        compose.onNodeWithText("20 de 70 XP en este nivel · 35 XP hoy").assertDoesNotExist()
    }

    @Test
    fun `tocar la mascota abre su pantalla y la descripcion dice quien es y en que nivel esta`() {
        var opened = 0
        card(progress(), onOpen = { opened++ })

        compose.onNodeWithContentDescription("Zorro, Alegre. Nivel 4.").performClick()

        assertEquals(1, opened)
    }

    @Test
    fun `tocar la barra no abre la pantalla de la mascota`() {
        var opened = 0
        card(progress(), onOpen = { opened++ })

        compose.onNodeWithContentDescription("Nivel 4").performClick()

        assertEquals(0, opened)
    }

    @Test
    fun `sin ninguna racha se invita a empezar y no se habla de perdida`() {
        card(progress(streak = StreakInfo(0, 0)))

        compose.onNodeWithText("Tu racha empieza con una rutina hecha.").assertExists()
        compose.onNodeWithText("Mejor racha", substring = true).assertDoesNotExist()
    }

    @Test
    fun `con la racha a cero solo se recuerda la mejor`() {
        card(progress(streak = StreakInfo(0, 7)))

        compose.onNodeWithText("Mejor racha: 7 días").assertExists()
        compose.onNodeWithText("Racha:", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Tu racha empieza con una rutina hecha.").assertDoesNotExist()
    }

    @Test
    fun `un solo dia de racha va en singular`() {
        card(progress(streak = StreakInfo(1, 1)))

        compose.onNodeWithText("Racha: 1 día").assertExists()
        compose.onNodeWithText("Mejor racha: 1 día").assertExists()
    }

    // ---- Pantalla «Mi mascota»

    private fun screen(
        progress: Progress?,
        onChooseMascot: (Mascot) -> Unit = {},
        onCosmetic: (Cosmetic) -> Unit = {},
    ) = compose.setContent {
        FocusDivergentTheme {
            MascotContent(
                uiState = MascotUiState(progress),
                snackbarHostState = SnackbarHostState(),
                onBack = {},
                onChooseMascot = onChooseMascot,
                onCosmetic = onCosmetic,
            )
        }
    }

    @Test
    fun `enseña quien es, su nivel, las monedas y la racha`() {
        screen(progress())

        compose.onNodeWithText("Mi mascota").assertExists()
        compose.onNodeWithText("Zorro, Alegre").assertExists()
        compose.onNodeWithContentDescription("Nivel 4").assertExists()
        compose.onNodeWithText("Monedas de enfoque: 12").assertExists()
        compose.onNodeWithText("Racha: 3 días").assertExists()
    }

    @Test
    fun `mientras no hay datos no enseña nada de la mascota`() {
        screen(null)

        compose.onNodeWithText("Tu compañero").assertDoesNotExist()
    }

    @Test
    fun `ofrece las tres mascotas, marca la elegida y avisa de la que se toca`() {
        val chosen = mutableListOf<Mascot>()
        screen(progress(), onChooseMascot = { chosen += it })

        compose.onNodeWithText("Zorro").assertIsSelected()
        compose.onNodeWithText("Gato").performClick()
        compose.onNodeWithText("Alien con gorra").performClick()

        assertEquals(listOf(Mascot.CAT, Mascot.ALIEN), chosen)
    }

    @Test
    fun `las etapas alcanzadas enseñan su nombre y las que faltan solo su nivel`() {
        screen(progress())

        compose.onNodeWithText("Cachorro").assertExists()
        compose.onNodeWithText("Alegre").assertExists()
        compose.onNodeWithText("Ahora").assertExists()
        // Etapa 3 (nivel 6) y siguientes: sin nombre todavía.
        compose.onNodeWithText("Durmiendo").assertDoesNotExist()
        compose.onNodeWithText("Con pañuelo").assertDoesNotExist()
        compose.onNodeWithText("Nivel 6").assertExists()
        compose.onNodeWithText("Nivel 10").assertExists()
        compose.onNodeWithText("Nivel 15").assertExists()
    }

    @Test
    fun `los nombres de las etapas son los de cada mascota`() {
        screen(progress(mascot = Mascot.CAT))

        compose.onNodeWithText("Jugando con la bola").assertExists()
    }

    @Test
    fun `la tienda ofrece lo que no se tiene con su precio y lo que se tiene con poner o quitar`() {
        screen(progress())

        compose.onNodeWithText("Comprar por 6").assertExists() // Noche
        compose.onNodeWithText("Comprar por 10").assertExists() // Bosque
        compose.onNodeWithText("Comprar por 12").assertExists() // Lámpara
        compose.onNodeWithText("Quitar").assertExists() // la planta que se lleva
        compose.onNodeWithText("Poner").assertExists() // los libros, comprados y sin poner
        compose.onNodeWithText("Puesto").assertExists() // el fondo tranquilo
        compose.onNodeWithText("Gratis").assertExists()
    }

    @Test
    fun `tocar un cosmetico avisa de cual`() {
        val tapped = mutableListOf<Cosmetic>()
        screen(progress(), onCosmetic = { tapped += it })

        // La tienda es larga: se baja hasta cada botón antes de pulsarlo.
        compose.onNodeWithText("Comprar por 6").performScrollTo().performClick()
        compose.onNodeWithText("Quitar").performScrollTo().performClick()
        compose.onNodeWithText("Poner").performScrollTo().performClick()

        assertEquals(listOf(Cosmetic.SCENERY_NIGHT, Cosmetic.PROP_PLANT, Cosmetic.PROP_BOOKS), tapped)
    }

    @Test
    fun `todo cosmetico tiene su nombre en la tienda`() {
        screen(progress())

        for (name in listOf("Tranquilo", "Noche", "Bosque", "Atardecer", "Nebulosa", "Planta", "Libros", "Lámpara", "Estrella")) {
            compose.onNodeWithText(name).assertExists()
        }
    }
}
