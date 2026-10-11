package com.focusdivergent.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

/**
 * Índice de texto completo de la Bóveda.
 *
 * Es una tabla FTS4 de contenido externo: no duplica el texto, apunta a `vault_nodes`, y Room
 * genera los triggers que la mantienen sincronizada. El SRD pedía FTS5, pero Room solo da soporte
 * nativo a FTS3/FTS4; nunca se escribe en esta tabla directamente.
 *
 * Usa el tokenizador `unicode61` en lugar del `simple` por defecto: ignora mayúsculas y acentos en
 * cualquier alfabeto, de modo que "musica" encuentra "Música" y "nandu" encuentra "Ñandú".
 */
@Fts4(contentEntity = VaultNodeEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "vault_nodes_fts")
data class VaultNodeFtsEntity(
    val title: String,
    @ColumnInfo(name = "markdown_content") val markdownContent: String?,
    val tags: String,
)
