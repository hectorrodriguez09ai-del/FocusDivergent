package com.focusdivergent.app.domain.usecase

import com.focusdivergent.app.domain.braindump.IdeaText
import com.focusdivergent.app.domain.model.BrainDump
import com.focusdivergent.app.domain.model.VaultNode
import com.focusdivergent.app.domain.repository.BrainDumpRepository
import com.focusdivergent.app.domain.repository.VaultRepository
import java.time.Clock
import javax.inject.Inject

/**
 * Guarda una idea en la bandeja de entrada (RF-5.1). Quita los espacios de los extremos y no guarda nada si no
 * queda texto. Devuelve `true` si guardó algo.
 */
class CaptureIdeaUseCase @Inject constructor(
    private val brainDump: BrainDumpRepository,
    private val clock: Clock,
) {

    suspend operator fun invoke(text: String): Boolean {
        val content = text.trim()
        if (content.isEmpty()) return false
        brainDump.add(BrainDump(content = content, timestamp = clock.instant()))
        return true
    }
}

/**
 * Pasa una idea de la bandeja a la Bóveda como nota en la raíz. El título es su primera línea y el cuerpo, la idea
 * entera, así que no se pierde nada aunque el título se recorte. Devuelve el id de la nota.
 */
class SaveIdeaToVaultUseCase @Inject constructor(
    private val brainDump: BrainDumpRepository,
    private val vault: VaultRepository,
    private val clock: Clock,
) {

    suspend operator fun invoke(idea: BrainDump): Long {
        val id = vault.save(
            VaultNode(
                parentId = null,
                title = IdeaText.titleOf(idea.content),
                markdownContent = idea.content,
                updatedAt = clock.instant(),
            ),
        )
        brainDump.markProcessed(idea.id)
        return id
    }
}
