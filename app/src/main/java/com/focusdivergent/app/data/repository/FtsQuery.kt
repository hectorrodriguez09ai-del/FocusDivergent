package com.focusdivergent.app.data.repository

private val WHITESPACE = Regex("\\s+")

/**
 * Convierte texto libre del usuario en una consulta `MATCH` segura para FTS4.
 *
 * FTS4 interpreta comillas, `*`, `-`, `AND`, `OR`, `NEAR`, paréntesis y `columna:` como sintaxis, y
 * lanza una excepción si están mal formados. Para evitarlo cada palabra se entrecomilla, con lo que
 * pasa a ser un término literal. Se descartan las comillas y los `*` que escriba el usuario y las
 * "palabras" sin ninguna letra ni dígito. La última palabra se marca como prefijo.
 *
 * Devuelve nulo si no queda ninguna palabra.
 */
internal fun String.toFtsMatchQuery(): String? {
    val terms = trim()
        .split(WHITESPACE)
        .map { it.replace("\"", "").replace("*", "") }
        .filter { term -> term.any(Char::isLetterOrDigit) }
    if (terms.isEmpty()) return null

    return terms.mapIndexed { index, term ->
        if (index == terms.lastIndex) "\"$term*\"" else "\"$term\""
    }.joinToString(" ")
}
