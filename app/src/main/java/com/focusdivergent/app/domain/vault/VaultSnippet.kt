package com.focusdivergent.app.domain.vault

import java.text.Normalizer

/** El trozo de una nota que se enseña bajo un resultado de búsqueda. */
object VaultSnippet {

    const val LENGTH = 110

    /**
     * Unos [LENGTH] caracteres de [content] alrededor de la primera palabra de [query] que aparezca, con «…» donde
     * se corta. Si ninguna aparece (por ejemplo, la búsqueda coincidió con el título) devuelve el principio.
     *
     * La comparación ignora mayúsculas y acentos, como la búsqueda de texto completo: «musica» localiza «Música».
     */
    fun of(content: String?, query: String, length: Int = LENGTH): String {
        val text = content?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        if (text.isEmpty()) return ""

        val flat = text.map(::baseChar).joinToString("")
        val at = query.split(Regex("\\s+"))
            .map { word -> word.filter(Char::isLetterOrDigit).map(::baseChar).joinToString("") }
            .filter { it.isNotEmpty() }
            .map { flat.indexOf(it) }
            .filter { it >= 0 }
            .minOrNull()

        if (at == null || text.length <= length) return text.take(length).let { if (text.length > length) "$it…" else it }

        // Un poco de contexto antes de la coincidencia, sin salirse del texto.
        val start = (at - length / 4).coerceAtLeast(0)
        val end = (start + length).coerceAtMost(text.length)
        return (if (start > 0) "…" else "") + text.substring(start, end).trim() + (if (end < text.length) "…" else "")
    }

    // Cada letra se reduce a su base en minúscula, siempre a un solo carácter, para que las posiciones coincidan con el original.
    private fun baseChar(c: Char): Char =
        Normalizer.normalize(c.toString(), Normalizer.Form.NFD).first().lowercaseChar()
}
