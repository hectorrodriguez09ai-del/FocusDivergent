package com.focusdivergent.app.presentation.markdown

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.focusdivergent.app.presentation.theme.FocusDivergentTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "es-rES-w411dp-h2000dp")
class MarkdownTest {

    @get:Rule
    val compose = createComposeRule()

    private fun text(s: String) = MdSpan.Text(s)

    // ---- Análisis

    @Test
    fun `titulos, parrafos y listas`() {
        val blocks = MarkdownParser.parse("# Título\n\nUn párrafo.\n\n- uno\n- dos\n\n3. tres\n4. cuatro")

        assertEquals(MdBlock.Heading(1, listOf(text("Título"))), blocks[0])
        assertEquals(MdBlock.Paragraph(listOf(text("Un párrafo."))), blocks[1])
        assertEquals(
            MdBlock.Items(ordered = false, start = 1, items = listOf(listOf(MdBlock.Paragraph(listOf(text("uno")))), listOf(MdBlock.Paragraph(listOf(text("dos")))))),
            blocks[2],
        )
        val numbered = blocks[3] as MdBlock.Items
        assertTrue(numbered.ordered)
        assertEquals(3, numbered.start)
        assertEquals(2, numbered.items.size)
    }

    @Test
    fun `negrita, cursiva, codigo y enlaces en linea`() {
        val paragraph = MarkdownParser.parse("**fuerte** y *suave* con `codigo` y [web](https://example.com)")
            .single() as MdBlock.Paragraph

        assertEquals(MdSpan.Strong(listOf(text("fuerte"))), paragraph.spans[0])
        assertEquals(MdSpan.Emphasis(listOf(text("suave"))), paragraph.spans[2])
        assertEquals(MdSpan.Code("codigo"), paragraph.spans[4])
        assertEquals(MdSpan.Link("https://example.com", listOf(text("web"))), paragraph.spans[6])
    }

    @Test
    fun `citas, codigo y separadores`() {
        val blocks = MarkdownParser.parse("> cita\n\n```\nval x = 1\n```\n\n---")

        assertEquals(MdBlock.Quote(listOf(MdBlock.Paragraph(listOf(text("cita"))))), blocks[0])
        assertEquals(MdBlock.CodeBlock("val x = 1"), blocks[1])
        assertEquals(MdBlock.Rule, blocks[2])
    }

    @Test
    fun `un salto de linea simple se respeta, porque asi se escribe en el movil`() {
        val paragraph = MarkdownParser.parse("primera\nsegunda").single() as MdBlock.Paragraph

        assertEquals(listOf(text("primera"), MdSpan.LineBreak, text("segunda")), paragraph.spans)
    }

    @Test
    fun `el HTML se ve como texto y las imagenes como su texto alternativo`() {
        val html = MarkdownParser.parse("<div>hola</div>").single() as MdBlock.Paragraph
        assertTrue((html.spans.single() as MdSpan.Text).text.contains("<div>"))

        val image = MarkdownParser.parse("![gato](https://example.com/gato.png)").single() as MdBlock.Paragraph
        assertEquals(listOf(text("gato")), image.spans)
    }

    @Test
    fun `un texto vacio no da bloques`() {
        assertTrue(MarkdownParser.parse("").isEmpty())
    }

    @Test
    fun `solo se abren enlaces web y de correo`() {
        assertTrue(isSafeLink("https://example.com"))
        assertTrue(isSafeLink("HTTP://example.com"))
        assertTrue(isSafeLink("mailto:a@b.com"))
        assertFalse(isSafeLink("javascript:alert(1)"))
        assertFalse(isSafeLink("intent://x#Intent;end"))
        assertFalse(isSafeLink("ejemplo.com"))
    }

    // ---- Dibujo

    @Test
    fun `el documento dibuja sus textos`() {
        compose.setContent {
            FocusDivergentTheme {
                MarkdownText("# Mi título\n\nUn texto con **negrita**.\n\n- primero\n- segundo\n\n> una cita\n\n```\ncodigo\n```")
            }
        }

        compose.onNodeWithText("Mi título").assertExists()
        compose.onNodeWithText("Un texto con negrita.").assertExists()
        compose.onNodeWithText("primero").assertExists()
        compose.onNodeWithText("segundo").assertExists()
        compose.onNodeWithText("una cita").assertExists()
        compose.onNodeWithText("codigo").assertExists()
    }

    @Test
    fun `una lista numerada empieza donde dice el texto`() {
        compose.setContent { FocusDivergentTheme { MarkdownText("5. cinco\n6. seis") } }

        compose.onNodeWithText("5.").assertExists()
        compose.onNodeWithText("6.").assertExists()
    }
}
