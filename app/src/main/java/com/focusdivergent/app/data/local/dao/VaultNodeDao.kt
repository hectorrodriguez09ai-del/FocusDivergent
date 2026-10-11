package com.focusdivergent.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.focusdivergent.app.data.local.entity.VaultNodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultNodeDao {

    /** Contenido de una carpeta. Con [parentId] nulo devuelve los nodos de la raíz. */
    @Query("SELECT * FROM vault_nodes WHERE parent_id IS :parentId ORDER BY title COLLATE NOCASE ASC")
    fun observeChildren(parentId: Long?): Flow<List<VaultNodeEntity>>

    /** Todas las carpetas, para construir rutas y elegir destino al mover una nota. */
    @Query("SELECT * FROM vault_nodes WHERE markdown_content IS NULL ORDER BY title COLLATE NOCASE ASC")
    fun observeFolders(): Flow<List<VaultNodeEntity>>

    /** La columna de etiquetas de cada nodo que tiene alguna (texto `#a #b`). */
    @Query("SELECT tags FROM vault_nodes WHERE tags != ''")
    fun observeTagColumns(): Flow<List<String>>

    @Query("SELECT * FROM vault_nodes WHERE id = :id")
    suspend fun getById(id: Long): VaultNodeEntity?

    /**
     * Búsqueda de texto completo en título, contenido y etiquetas.
     *
     * [query] usa la sintaxis MATCH de FTS4: `palabra`, `prefijo*`, `"frase exacta"`.
     */
    @Query(
        """
        SELECT vault_nodes.* FROM vault_nodes
        JOIN vault_nodes_fts ON vault_nodes.id = vault_nodes_fts.rowid
        WHERE vault_nodes_fts MATCH :query
        """,
    )
    fun search(query: String): Flow<List<VaultNodeEntity>>

    /** Inserta o actualiza. Devuelve el id de la fila nueva, o -1 si fue una actualización. */
    @Upsert
    suspend fun upsert(node: VaultNodeEntity): Long

    /** Borra el nodo y, si es una carpeta, todo su contenido (borrado en cascada). */
    @Delete
    suspend fun delete(node: VaultNodeEntity)
}
