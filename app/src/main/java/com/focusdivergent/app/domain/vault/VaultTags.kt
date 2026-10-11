package com.focusdivergent.app.domain.vault

/** Reglas de las etiquetas de la Bóveda: sin almohadilla, sin espacios y sin repetidas. */
object VaultTags {

    const val MAX_LENGTH = 30
    const val MAX_SUGGESTIONS = 8

    /** `" #Mi idea "` → `Mi_idea`. Una etiqueta no puede llevar espacios, porque se guardan separadas por ellos. */
    fun normalize(raw: String): String =
        raw.trim().trimStart('#').trim().replace(Regex("\\s+"), "_").take(MAX_LENGTH)

    /** Añade [raw] a [tags] si queda algo tras normalizarla y no estaba ya (sin distinguir mayúsculas). */
    fun add(tags: List<String>, raw: String): List<String> {
        val tag = normalize(raw)
        if (tag.isEmpty() || tags.any { it.equals(tag, ignoreCase = true) }) return tags
        return tags + tag
    }

    /** Etiquetas ya usadas en otras notas que esta aún no tiene, las más usadas primero. */
    fun suggestions(used: List<String>, current: List<String>, limit: Int = MAX_SUGGESTIONS): List<String> =
        used.filter { candidate -> current.none { it.equals(candidate, ignoreCase = true) } }.take(limit)
}
