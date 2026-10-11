package com.focusdivergent.app.data.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.focusdivergent.app.domain.model.UsageSnapshot
import com.focusdivergent.app.domain.repository.UsageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lee el uso de apps de `UsageStatsManager`. Requiere el permiso de acceso al uso (`PACKAGE_USAGE_STATS`). */
class UsageStatsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : UsageRepository {

    // MOVE_TO_FOREGROUND/MOVE_TO_BACKGROUND están marcadas como obsoletas desde la API 29, pero tienen
    // los mismos valores (1 y 2) que ACTIVITY_RESUMED/ACTIVITY_PAUSED y funcionan desde la API 26.
    @Suppress("DEPRECATION")
    override suspend fun readToday(): UsageSnapshot = withContext(Dispatchers.IO) {
        val manager = context.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val usageEvents = manager.queryEvents(startOfDay, now)
            ?: return@withContext UsageSnapshot(foregroundPackage = null, usedMillisByPackage = emptyMap())

        val events = ArrayList<RawUsageEvent>()
        val event = UsageEvents.Event()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            val isResume = when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> true
                UsageEvents.Event.MOVE_TO_BACKGROUND -> false
                else -> continue
            }
            events += RawUsageEvent(
                packageName = event.packageName,
                className = event.className.orEmpty(),
                isResume = isResume,
                timestampMillis = event.timeStamp,
            )
        }
        computeUsageSnapshot(events, windowStartMillis = startOfDay, nowMillis = now)
    }
}
