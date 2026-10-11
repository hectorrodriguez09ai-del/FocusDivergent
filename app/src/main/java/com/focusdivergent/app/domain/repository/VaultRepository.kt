package com.focusdivergent.app.domain.repository

import com.focusdivergent.app.domain.model.VaultNode
import kotlinx.coroutines.flow.Flow

interface VaultRepository {

    /** Contenido de una carpeta. Con [parentId] nulo devuelve los nodos de la raíz. */
    fun observeChildren(parentId: Long?): Flow<List<VaultNode>>

    /** Todas las carpetas de la Bóveda, de cualquier nivel, por orden alfabético. */
    fun observeFolders(): Flow<List<VaultNode>>

    /** Las etiquetas en uso (sin almohadilla), las más usadas primero. */
    fun observeTags(): Flow<List<String>>

    suspend fun getById(id: Long): VaultNode?

    /**
     * Búsqueda de texto completo en título, contenido y etiquetas.
     *
     * Acepta texto libre tal como lo escribe el usuario: nunca falla por signos raros. Se buscan
     * todas las palabras y la última se trata como prefijo, así que "penta" encuentra "pentatónica".
     * Una consulta sin palabras devuelve una lista vacía.
     */
    fun search(query: String): Flow<List<VaultNode>>

    /** Crea o actualiza el nodo y devuelve su id definitivo. */
    suspend fun save(node: VaultNode): Long

    /** Borra el nodo y, si es una carpeta, todo su contenido. */
    suspend fun delete(node: VaultNode)
}
