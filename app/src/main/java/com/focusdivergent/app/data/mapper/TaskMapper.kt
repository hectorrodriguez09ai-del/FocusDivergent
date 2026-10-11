package com.focusdivergent.app.data.mapper

import com.focusdivergent.app.data.local.entity.TaskEntity
import com.focusdivergent.app.domain.model.Task
import java.time.Instant

fun TaskEntity.toDomain(): Task = Task(
    id = id,
    title = title,
    type = type,
    energyLevel = energyLevel,
    status = status,
    startTime = startTime?.let(Instant::ofEpochMilli),
    endTime = endTime?.let(Instant::ofEpochMilli),
    recurrenceRule = recurrenceRule,
    estimatedMinutes = estimatedMinutes,
    completedAt = completedAt?.let(Instant::ofEpochMilli),
)

fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id,
    title = title,
    type = type,
    energyLevel = energyLevel,
    status = status,
    startTime = startTime?.toEpochMilli(),
    endTime = endTime?.toEpochMilli(),
    recurrenceRule = recurrenceRule,
    estimatedMinutes = estimatedMinutes,
    completedAt = completedAt?.toEpochMilli(),
)
