package com.focusdivergent.app.data.mapper

import com.focusdivergent.app.data.local.entity.TimeExtensionEntity
import com.focusdivergent.app.domain.model.TimeExtension
import java.time.Instant

fun TimeExtensionEntity.toDomain(): TimeExtension = TimeExtension(
    id = id,
    packageName = packageName,
    grantedAt = Instant.ofEpochMilli(grantedAt),
    minutes = minutes,
)

fun TimeExtension.toEntity(): TimeExtensionEntity = TimeExtensionEntity(
    id = id,
    packageName = packageName,
    grantedAt = grantedAt.toEpochMilli(),
    minutes = minutes,
)
