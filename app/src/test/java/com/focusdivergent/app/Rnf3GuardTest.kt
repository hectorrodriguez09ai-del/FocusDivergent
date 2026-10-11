package com.focusdivergent.app

import java.io.File
import java.text.Normalizer
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * RNF-3, «cero penalización visual», vigilado de forma automática sobre todo el código y los recursos: ningún
 * rojo en ningún sitio y ningún texto con reproche, alarma o gritos. Si alguien añade una pantalla o un texto
 * que lo incumple, falla aquí y no en la revisión de un usuario.
 *
 * Gradle ejecuta los tests con la carpeta del módulo (`app`) como directorio de trabajo.
 */
class Rnf3GuardTest {

    private val main = File("src/main")

    // ---- Colores

    private data class Rgb(val r: Float, val g: Float, val b: Float)

    /** Tono HSV en grados, o nulo si es un gris o casi negro, sin tono apreciable. */
    private fun hue(c: Rgb): Float? {
        val mx = max(c.r, max(c.g, c.b))
        val mn = min(c.r, min(c.g, c.b))
        val delta = mx - mn
        if (mx == 0f || delta / mx < 0.25f || delta < 0.08f) return null
        return when (mx) {
            c.r -> 60f * (((c.g - c.b) / delta).mod(6f))
            c.g -> 60f * ((c.b - c.r) / delta + 2f)
            else -> 60f * ((c.r - c.g) / delta + 4f)
        }
    }

    private fun isReddish(c: Rgb): Boolean = hue(c)?.let { it < 20f || it > 340f } == true

    private fun parseHex(hex: String): Rgb? {
        // #RRGGBB, #AARRGGBB, 0xAARRGGBB. Un color totalmente transparente no se ve, no cuenta.
        val digits = hex.removePrefix("#").removePrefix("0x").removePrefix("0X")
        val argb = when (digits.length) {
            6 -> "FF$digits"
            8 -> digits
            else -> return null
        }
        if (argb.substring(0, 2).equals("00", ignoreCase = true)) return null
        fun channel(from: Int) = argb.substring(from, from + 2).toInt(16) / 255f
        return Rgb(channel(2), channel(4), channel(6))
    }

    private val colorLiteral = Regex("""#[0-9A-Fa-f]{8}\b|#[0-9A-Fa-f]{6}\b|Color\(0x[0-9A-Fa-f]{8}\)""")

    private fun sourceFiles(): List<File> =
        main.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }.toList()

    private fun colorsIn(file: File): List<Pair<String, Rgb>> = colorLiteral.findAll(file.readText()).mapNotNull { match ->
        val literal = match.value.removePrefix("Color(").removeSuffix(")")
        parseHex(literal)?.let { match.value to it }
    }.toList()

    @Test
    fun `ningun color de todo el codigo y los recursos es rojo`() {
        val files = sourceFiles()
        assertTrue("no se encontraron fuentes en ${main.absolutePath}", files.size > 50)

        var inspected = 0
        val offenders = mutableListOf<String>()
        for (file in files) {
            for ((literal, rgb) in colorsIn(file)) {
                inspected++
                if (isReddish(rgb)) offenders += "${file.name}: $literal"
            }
        }

        assertTrue("se esperaban muchos colores revisados, hubo $inspected", inspected > 50)
        assertEquals("colores rojizos (RNF-3): $offenders", emptyList<String>(), offenders)
    }

    @Test
    fun `no se usan los colores rojos con nombre de Compose`() {
        val offenders = sourceFiles()
            .filter { it.extension == "kt" }
            .filter { Regex("""\bColor\.(Red|Magenta)\b""").containsMatchIn(it.readText()) }
            .map { it.name }

        assertEquals("usan Color.Red o Color.Magenta: $offenders", emptyList<String>(), offenders)
    }

    @Test
    fun `el detector de rojos reconoce los rojos tipicos`() {
        // Sin esto, los tests anteriores pasarían aunque el detector no detectara nada.
        listOf("#FF0000", "#FFDC2626", "#B3261E", "0xFFF2B8B5", "#93000A", "#FFFF4444").forEach {
            assertTrue(it, isReddish(parseHex(it)!!))
        }
        listOf("#448AFF", "#A974FF", "#FCD34D", "#8A5A00", "#000000", "#FFFFFF", "#7DD3FC").forEach {
            assertFalse(it, isReddish(parseHex(it)!!))
        }
        assertEquals(null, parseHex("#00FF0000"))
    }

    // ---- Lenguaje

    private val bannedStems = listOf(
        "atrasad", "vencid", "fallad", "fallast", "fracas", "culpa", "vago", "perezos", "deberias", "tienes que", "debes ",
        "no has ", "no cumpl", "incumpl", "castig", "penaliz", "peligro", "urgente", "advertencia", "prohibid", "error",
    )

    private fun normalize(text: String) =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    /** Qué incumple RNF-3 en [text]: palabras de reproche o alarma, exclamaciones o palabras en mayúsculas. */
    private fun violations(text: String): List<String> = buildList {
        val plain = normalize(text)
        bannedStems.filter { plain.contains(it) }.forEach { add("«$it»") }
        if (text.contains('!') || text.contains('¡')) add("exclamación")
        // Gritar: una palabra de 4 o más letras toda en mayúsculas.
        if (Regex("""\b\p{Lu}{4,}\b""").containsMatchIn(text)) add("mayúsculas")
    }

    private fun allTexts(): List<Pair<String, String>> {
        val xml = File(main, "res/values/strings.xml")
        assertTrue("falta ${xml.absolutePath}", xml.exists())
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml).documentElement
        val result = mutableListOf<Pair<String, String>>()
        for (tag in listOf("string", "item")) {
            val nodes = root.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                val element = nodes.item(i) as Element
                val name = element.getAttribute("name").ifEmpty { (element.parentNode as Element).getAttribute("name") }
                result += name to element.textContent
            }
        }
        return result
    }

    @Test
    fun `ningun texto de la interfaz tiene reproche, alarma ni gritos`() {
        val texts = allTexts()
        assertTrue("se esperaban muchos textos, hubo ${texts.size}", texts.size > 150)

        val offenders = texts.mapNotNull { (name, text) ->
            violations(text).takeIf { it.isNotEmpty() }?.let { "$name: $it → «$text»" }
        }

        assertEquals("textos que incumplen RNF-3:\n${offenders.joinToString("\n")}", emptyList<String>(), offenders)
    }

    @Test
    fun `el detector de lenguaje reconoce el reproche y no se lo toma con los textos amables`() {
        listOf(
            "Tienes tareas atrasadas",
            "¡No lo has conseguido!",
            "Has fallado, otra vez",
            "Es culpa tuya",
            "Tarea vencida",
            "ATENCIÓN: límite superado",
            "Deberías esforzarte más",
            "Hay un error grave",
        ).forEach { assertTrue(it, violations(it).isNotEmpty()) }

        listOf(
            "Llegaste a los 30 minutos que elegiste para hoy. Mañana empieza de nuevo.",
            "Haber empezado ya cuenta.",
            "Lo dejo por ahora",
            "Ponle un nombre, aunque sea corto.",
            "Cuidado del dispositivo",
            "FocusDivergent",
        ).forEach { assertTrue(it, violations(it).isEmpty()) }
    }
}
