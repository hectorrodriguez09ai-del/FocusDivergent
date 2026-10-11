package com.focusdivergent.app.data.mapper

import com.focusdivergent.app.data.local.entity.VaultNodeEntity
import com.focusdivergent.app.domain.model.VaultNode
import java.time.Instant

private val TAG_SEPARATOR = Regex("\\s+")

/** Las etiquetas de una columna `#a #b` como lista sin almohadilla. */
internal fun String.toTagList(): List<String> = split(TAG_SEPARATOR).map { it.trimStart('#') }.filter { it.isNotEmpty() }

// En la base de datos las etiquetas son un solo texto `#Documentos #Ideas`; el dominio las ve como lista sin almohadilla.
fun VaultNodeEntity.toDomain(): VaultNode = VaultNode(
    id = id,
    parentId = parentId,
    title = title,
    markdownContent = markdownContent,
    tags = tags.toTagList(),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

fun VaultNode.toEntity(): VaultNodeEntity = VaultNodeEntity(
    id = id,
    parentId = parentId,
    title = title,
    markdownContent = markdownContent,
    tags = tags
        .map { it.trim().trimStart('#') }
        .filter { it.isNotEmpty() }
        .joinToString(" ") { "#$it" },
    updatedAt = updatedAt.toEpochMilli(),
)
