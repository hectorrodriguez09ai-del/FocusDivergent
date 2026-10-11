package com.focusdivergent.app.presentation.audio

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.focusdivergent.app.domain.audio.AmbientSound
import com.focusdivergent.app.presentation.theme.FocusDivergentTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "es-rES-w411dp-h2000dp")
class AudioScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val idle = AudioUiState(selected = AmbientSound.WHITE_NOISE, volume = 0.4f)

    private fun show(
        state: AudioUiState,
        onSelect: (AmbientSound) -> Unit = {},
        onToggle: () -> Unit = {},
        onStop: () -> Unit = {},
        onVolume: (Float) -> Unit = {},
        onSleep: (Int?) -> Unit = {},
    ) = compose.setContent {
        FocusDivergentTheme {
            AudioContent(
                uiState = state,
                onBack = {},
                onSelect = onSelect,
                onTogglePlay = onToggle,
                onStop = onStop,
                onVolume = onVolume,
                onSleepTimer = onSleep,
            )
        }
    }

    @Test
    fun `sin nada sonando enseña los cuatro sonidos con su explicacion y el boton de reproducir`() {
        var toggled = 0
        show(idle, onToggle = { toggled++ })

        compose.onNodeWithText("Ruido blanco").assertIsSelected()
        compose.onNodeWithText("Ruido marrón").assertExists()
        compose.onNodeWithText("Binaural").assertExists()
        compose.onNodeWithText("Lluvia").assertExists()
        compose.onNodeWithText("Lluvia suave, creada en tu móvil.").assertExists()
        compose.onNodeWithText("Parar").assertDoesNotExist()
        compose.onNodeWithText("Reproducir").performClick()

        assertEquals(1, toggled)
    }

    @Test
    fun `elegir un sonido avisa`() {
        val chosen = mutableListOf<AmbientSound>()
        show(idle, onSelect = { chosen += it })

        compose.onNodeWithText("Lluvia").performClick()
        compose.onNodeWithText("Binaural").performClick()

        assertEquals(listOf(AmbientSound.RAIN, AmbientSound.BINAURAL), chosen)
    }

    @Test
    fun `el aviso de auriculares solo sale con el binaural elegido`() {
        show(idle.copy(selected = AmbientSound.BINAURAL))
        compose.onNodeWithText("Se nota solo con auriculares. Es un sonido más, no un tratamiento.").assertExists()
    }

    @Test
    fun `con otro sonido elegido no sale el aviso de auriculares`() {
        show(idle.copy(selected = AmbientSound.RAIN))
        compose.onNodeWithText("Se nota solo con auriculares. Es un sonido más, no un tratamiento.").assertDoesNotExist()
    }

    @Test
    fun `sonando muestra que suena, lo que falta para parar, pausar y parar`() {
        var toggled = 0
        var stopped = 0
        show(
            idle.copy(selected = AmbientSound.RAIN, active = true, playing = true, sleepMinutes = 30, remainingMinutes = 12),
            onToggle = { toggled++ },
            onStop = { stopped++ },
        )

        compose.onNodeWithText("Sonando: Lluvia").assertExists()
        compose.onNodeWithText("Se parará en 12 min").assertExists()
        compose.onNodeWithText("Pausar").performClick()
        compose.onNodeWithText("Parar").performScrollTo().performClick()

        assertEquals(1, toggled)
        assertEquals(1, stopped)
    }

    @Test
    fun `en pausa lo dice y el boton pasa a reanudar`() {
        show(idle.copy(selected = AmbientSound.RAIN, active = true, playing = false))

        compose.onNodeWithText("En pausa: Lluvia").assertExists()
        compose.onNodeWithText("Reanudar").assertExists()
    }

    @Test
    fun `el volumen se dice en porcentaje, avisa de que su maximo es moderado y se mueve`() {
        val levels = mutableListOf<Float>()
        show(idle, onVolume = { levels += it })

        compose.onNodeWithText("El volumen máximo es moderado, para cuidar el oído.").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("Volumen: 40 %")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.8f) }

        assertEquals(listOf(0.8f), levels)
    }

    @Test
    fun `parar solo en ofrece sin limite, 15, 30 y 60 minutos y marca el elegido`() {
        val chosen = mutableListOf<Int?>()
        show(idle.copy(sleepMinutes = 30), onSleep = { chosen += it })

        compose.onNodeWithText("Parar solo en").performScrollTo().assertExists()
        compose.onNodeWithText("30 min").assertIsSelected()
        compose.onNodeWithText("15 min").performScrollTo().performClick()
        compose.onNodeWithText("60 min").performScrollTo().performClick()
        compose.onNodeWithText("Sin límite").performScrollTo().performClick()

        assertEquals(listOf(15, 60, null), chosen)
    }

    @Test
    fun `sin limite esta marcado cuando no hay temporizador`() {
        show(idle.copy(sleepMinutes = null))
        compose.onNodeWithText("Sin límite").performScrollTo().assertIsSelected()
    }
}
