package com.focusdivergent.app.domain.braindump

/** Cómo se resume una idea cuando hace falta un título corto (el de una tarea o el de una nota). */
object IdeaText {

    const val MAX_TITLE = 80

    /**
     * Primera línea con texto, recortada a [MAX_TITLE] caracteres (en un límite de palabra si cabe) y con «…» si
     * se ha cortado. Una idea en blanco da una cadena vacía.
     */
    fun titleOf(content: String): String {
        val line = content.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return ""
        if (line.length <= MAX_TITLE) return line
        val cut = line.take(MAX_TITLE)
        val atWord = cut.lastIndexOf(' ').takeIf { it >= MAX_TITLE / 2 } ?: cut.length
        return cut.take(atWord).trimEnd() + "…"
    }
}
