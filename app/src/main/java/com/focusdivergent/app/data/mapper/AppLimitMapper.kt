package com.focusdivergent.app.data.mapper

import com.focusdivergent.app.data.local.entity.AppLimitEntity
import com.focusdivergent.app.domain.model.AppLimit

fun AppLimitEntity.toDomain(): AppLimit = AppLimit(
    packageName = packageName,
    dailyMinutesAllowed = dailyMinutesAllowed,
    isStrictlyBlocked = isStrictlyBlocked,
    extensionsPerDay = extensionsPerDay,
    isLeisure = isLeisure,
)

fun AppLimit.toEntity(): AppLimitEntity = AppLimitEntity(
    packageName = packageName,
    dailyMinutesAllowed = dailyMinutesAllowed,
    isStrictlyBlocked = isStrictlyBlocked,
    extensionsPerDay = extensionsPerDay,
    isLeisure = isLeisure,
)
