package com.focusdivergent.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Un cambio de límite que espera a medianoche (ver `LimitChangePolicy`). Una fila por app: un cambio nuevo
 * sustituye al anterior.
 *
 * Con [remove] la app pierde su límite al llegar [appliesAt] y los demás campos no se usan. No hay clave
 * foránea hacia `app_limits`: el cambio puede ser, precisamente, quitarlo.
 */
@Entity(tableName = "pending_limit_changes")
data class PendingLimitChangeEntity(
    @PrimaryKey @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "daily_minutes_allowed") val dailyMinutesAllowed: Int,
    @ColumnInfo(name = "is_strictly_blocked") val isStrictlyBlocked: Boolean,
    @ColumnInfo(name = "extensions_per_day") val extensionsPerDay: Int,
    val remove: Boolean,
    @ColumnInfo(name = "applies_at") val appliesAt: Long,
    @ColumnInfo(name = "is_leisure", defaultValue = "0") val isLeisure: Boolean = false,
)
