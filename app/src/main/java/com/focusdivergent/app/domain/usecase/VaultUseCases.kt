package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.braindump.IdeaText
import com.focusdivergent.app.domain.model.VaultNode
import com.focusdivergent.app.domain.repository.VaultRepository
import com.focusdivergent.app.domain.vault.VaultTags
import com.focusdivergent.app.domain.vault.VaultTrash
import java.time.Clock
import javax.inject.Inject

/**
 * Guarda una nota de la Bóveda (nueva o existente) con la hora de ahora como última modificación.
 *
 * Sin título, usa la primera línea del cuerpo, así que escribir sin ponerle nombre nunca deja una nota «sin
 * identificar» en la lista. Las etiquetas se normalizan y se quitan las repetidas. Devuelve la nota guardada, ya
 * con su id definitivo.
 */
class SaveNoteUseCase @Inject constructor(
    private val vault: VaultRepository,
    private val clock: Clock,
) {

    suspend operator fun invoke(
        existing: VaultNode?,
        parentId: Long?,
        title: String,
        body: String,
        tags: List<String>,
    ): VaultNode {
        val node = (existing ?: VaultNode(title = "", markdownContent = "")).copy(
            parentId = parentId,
            title = title.trim().ifEmpty { IdeaText.titleOf(body) },
            markdownContent = body,
            tags = tags.fold(emptyList()) { acc, tag -> VaultTags.add(acc, tag) },
            updatedAt = clock.instant(),
        )
        return node.copy(id = vault.save(node))
    }
}

/** Crea una carpeta o le cambia el nombre. Sin nombre no hace nada y devuelve nulo. */
class SaveFolderUseCase @Inject constructor(
    private val vault: VaultRepository,
    private val clock: Clock,
) {

    suspend operator fun invoke(existing: VaultNode?, parentId: Long?, name: String): VaultNode? {
        val title = name.trim()
        if (title.isEmpty()) return null
        // Cambiar el nombre actualiza la fila existente: el contenido de la carpeta no se toca.
        val folder = (existing ?: VaultNode(title = title)).copy(
            parentId = if (existing != null) existing.parentId else parentId,
            title = title,
            updatedAt = clock.instant(),
        )
        return folder.copy(id = vault.save(folder))
    }
}

/**
 * Borra una nota y la deja en la papelera unos segundos. Una carpeta se borra con todo lo que contiene y no se
 * puede deshacer: la pantalla pide confirmación antes de llamar aquí.
 */
class DeleteVaultNodeUseCase @Inject constructor(
    private val vault: VaultRepository,
    private val trash: VaultTrash,
) {

    suspend operator fun invoke(node: VaultNode) {
        vault.delete(node)
        if (!node.isFolder) trash.put(node)
    }
}

/**
 * Deshace el último borrado de una nota: vuelve con su mismo id y su contenido. Si su carpeta se borró mientras
 * tanto, vuelve a la raíz, para que no se pierda.
 */
class RestoreDeletedNoteUseCase @Inject constructor(
    private val vault: VaultRepository,
    private val trash: VaultTrash,
) {

    suspend operator fun invoke() {
        val note = trash.lastDeleted.value ?: return
        val parentExists = note.parentId == null || vault.getById(note.parentId) != null
        vault.save(if (parentExists) note else note.copy(parentId = null))
        trash.clear(note)
    }
}
