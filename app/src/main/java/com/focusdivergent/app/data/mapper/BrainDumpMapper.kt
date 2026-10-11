package com.focusdivergent.app.data.mapper

import com.focusdivergent.app.data.local.entity.BrainDumpEntity
import com.focusdivergent.app.domain.model.BrainDump
import java.time.Instant

fun BrainDumpEntity.toDomain(): BrainDump = BrainDump(
    id = id,
    content = content,
    timestamp = Instant.ofEpochMilli(timestamp),
    isProcessed = isProcessed,
)

fun BrainDump.toEntity(): BrainDumpEntity = BrainDumpEntity(
    id = id,
    content = content,
    timestamp = timestamp.toEpochMilli(),
    isProcessed = isProcessed,
)
