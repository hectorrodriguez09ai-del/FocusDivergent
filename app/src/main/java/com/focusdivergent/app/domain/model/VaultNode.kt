package com.focusdivergent.app.domain.model

import java.time.Instant

/**
 * Nodo de la Bóveda de Hiperfocos (RF-5.2): una carpeta o una nota Markdown.
 *
 * - [parentId] nulo = nodo en la raíz.
 * - [markdownContent] nulo = el nodo es una carpeta.
 * - [tags] van sin almacenar la almohadilla: `Documentos`, no `#Documentos`.
 * - [updatedAt] es la última vez que se tocó; [Instant.EPOCH] si no se sabe (lo guardado antes de existir este dato).
 */
data class VaultNode(
    val id: Long = 0,
    val parentId: Long? = null,
    val title: String,
    val markdownContent: String? = null,
    val tags: List<String> = emptyList(),
    val updatedAt: Instant = Instant.EPOCH,
) {
    val isFolder: Boolean get() = markdownContent == null
}
