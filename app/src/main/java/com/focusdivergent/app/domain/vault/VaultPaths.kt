package com.focusdivergent.app.domain.vault

import com.focusdivergent.app.domain.model.VaultNode

/** Rutas dentro de la Bóveda, a partir del conjunto de carpetas. */
object VaultPaths {

    /**
     * Las carpetas que hay que atravesar para llegar a [folderId], de la raíz hacia dentro, incluida la propia
     * [folderId]. Vacía para la raíz (nulo) o para una carpeta que ya no existe.
     */
    fun folderChain(folderId: Long?, folders: Map<Long, VaultNode>): List<VaultNode> {
        val chain = ArrayDeque<VaultNode>()
        var current = folderId
        // El tope evita un bucle infinito si alguna vez los datos tuvieran un ciclo.
        while (current != null && chain.size <= folders.size) {
            val folder = folders[current] ?: return emptyList()
            chain.addFirst(folder)
            current = folder.parentId
        }
        return chain.toList()
    }

    /** «Música › Guitarra»; vacío en la raíz. */
    fun label(folderId: Long?, folders: Map<Long, VaultNode>, separator: String = " › "): String =
        folderChain(folderId, folders).joinToString(separator) { it.title }
}
