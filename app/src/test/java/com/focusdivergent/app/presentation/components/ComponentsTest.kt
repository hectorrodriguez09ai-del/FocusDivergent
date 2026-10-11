package com.focusdivergent.app.presentation.components

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.focusdivergent.app.domain.model.EnergyLevel
import com.focusdivergent.app.presentation.navigation.MainBottomBar
import com.focusdivergent.app.presentation.navigation.TopLevelDestination
import com.focusdivergent.app.presentation.theme.FocusDivergentTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ComponentsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `el indicador de energia se anuncia como un solo elemento con su nivel`() {
        compose.setContent {
            FocusDivergentTheme { EnergyIndicator(EnergyLevel.LOW) }
        }

        // Un lector de pantalla oye «Energía: Baja», no tres barras sueltas y una palabra.
        compose.onNodeWithContentDescription("Energía: Baja").assertExists()
        compose.onAllNodesWithContentDescription("Energía: Baja").assertCountEquals(1)
    }

    @Test
    fun `cada nivel de energia tiene su propia etiqueta`() {
        compose.setContent {
            FocusDivergentTheme {
                androidx.compose.foundation.layout.Column {
                    EnergyLevel.entries.forEach { EnergyIndicator(it) }
                }
            }
        }

        compose.onNodeWithContentDescription("Energía: Baja").assertExists()
        compose.onNodeWithContentDescription("Energía: Media").assertExists()
        compose.onNodeWithContentDescription("Energía: Alta").assertExists()
    }

    @Test
    fun `el selector de energia marca la opcion elegida y avisa al tocar otra`() {
        var selected by mutableStateOf<EnergyLevel?>(null)
        compose.setContent {
            FocusDivergentTheme { EnergySelector(selected = selected, onSelect = { selected = it }) }
        }

        compose.onAllNodes(isSelectable()).assertCountEquals(3)
        compose.onNodeWithText("Media").assertIsNotSelected()

        compose.onNodeWithText("Alta").performClick()

        assertEquals(EnergyLevel.HIGH, selected)
        compose.onNodeWithText("Alta").assertIsSelected()
        compose.onNodeWithText("Baja").assertIsNotSelected()
    }

    @Test
    fun `una tarjeta de opcion expone si esta elegida y responde al toque`() {
        var selected by mutableStateOf(false)
        compose.setContent {
            FocusDivergentTheme {
                ChoiceCard(
                    title = "Firme",
                    description = "Al llegar al límite, la app se cierra hasta mañana.",
                    selected = selected,
                    onClick = { selected = true },
                )
            }
        }

        compose.onNodeWithText("Firme").assertIsNotSelected()
        compose.onNodeWithText("Firme").performClick()
        compose.onNodeWithText("Firme").assertIsSelected()
    }

    @Test
    fun `los atajos de duracion eligen un valor y al repetir lo quitan`() {
        var duration by mutableStateOf<Int?>(null)
        compose.setContent {
            FocusDivergentTheme {
                DurationChips(options = listOf(5, 15, 30, 60), selected = duration, onSelect = { duration = it })
            }
        }

        compose.onNodeWithText("15 min").performClick()
        assertEquals(15, duration)

        compose.onNodeWithText("15 min").performClick()
        assertEquals(null, duration)
    }

    @Test
    fun `la barra inferior muestra las cuatro pestanas con texto y marca la actual`() {
        var current by mutableStateOf(TopLevelDestination.TODAY)
        compose.setContent {
            FocusDivergentTheme { MainBottomBar(current = current, onSelect = { current = it }) }
        }

        listOf("Hoy", "Tareas", "Bóveda", "Bloqueos").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("Hoy").assertIsSelected()

        compose.onNodeWithText("Bloqueos").performClick()

        assertEquals(TopLevelDestination.BLOCKS, current)
        compose.onNodeWithText("Bloqueos").assertIsSelected()
        compose.onNodeWithText("Hoy").assertIsNotSelected()
    }
}
