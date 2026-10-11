package com.focusdivergent.app.data.mapper

import com.focusdivergent.app.data.local.entity.UserStatsEntity
import com.focusdivergent.app.domain.model.UserStats

fun UserStatsEntity.toDomain(): UserStats = UserStats(bestStreak = bestStreak)
