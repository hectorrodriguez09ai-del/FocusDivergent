package com.focusdivergent.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Nodo de la Bóveda de Hiperfocos (RF-5.2): una carpeta o una nota Markdown.
 *
 * - [parentId] nulo = nodo en la raíz. Borrar una carpeta borra todo lo que contiene.
 * - [markdownContent] nulo = el nodo es una carpeta.
 * - [tags] son etiquetas separadas por espacios (p. ej. `#Documentos #Ideas`), el formato que
 *   mejor indexa la búsqueda de texto completo.
 * - [updatedAt] es la última vez que se tocó, en milisegundos desde 1970; 0 en lo anterior a la versión 5 de la base.
 */
@Entity(
    tableName = "vault_nodes",
    foreignKeys = [
        ForeignKey(
            entity = VaultNodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("parent_id")],
)
data class VaultNodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "parent_id") val parentId: Long? = null,
    val title: String,
    @ColumnInfo(name = "markdown_content") val markdownContent: String? = null,
    val tags: String = "",
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = 0,
)
