package com.focusdivergent.app.data.mapper

import com.focusdivergent.app.data.local.entity.PendingLimitChangeEntity
import com.focusdivergent.app.domain.model.AppLimit
import com.focusdivergent.app.domain.model.PendingLimitChange
import java.time.Instant

fun PendingLimitChangeEntity.toDomain(): PendingLimitChange = PendingLimitChange(
    packageName = packageName,
    newLimit = if (remove) {
        null
    } else {
        AppLimit(packageName, dailyMinutesAllowed, isStrictlyBlocked, extensionsPerDay, isLeisure)
    },
    appliesAt = Instant.ofEpochMilli(appliesAt),
)

fun PendingLimitChange.toEntity(): PendingLimitChangeEntity = PendingLimitChangeEntity(
    packageName = packageName,
    dailyMinutesAllowed = newLimit?.dailyMinutesAllowed ?: 0,
    isStrictlyBlocked = newLimit?.isStrictlyBlocked ?: false,
    extensionsPerDay = newLimit?.extensionsPerDay ?: 0,
    remove = newLimit == null,
    appliesAt = appliesAt.toEpochMilli(),
    isLeisure = newLimit?.isLeisure ?: false,
)
