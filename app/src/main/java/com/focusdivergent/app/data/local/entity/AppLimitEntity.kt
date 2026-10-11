package com.focusdivergent.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Límite diario de una aplicación (RF-1.1). La clave es el nombre de paquete: una app, un límite.
 *
 * Con [isStrictlyBlocked] la app queda bloqueada siempre, sin depender del tiempo consumido.
 * [extensionsPerDay] son las ampliaciones diarias con reto matemático que permite: 3 en el nivel flexible, 0 en el firme.
 */
@Entity(tableName = "app_limits")
data class AppLimitEntity(
    @PrimaryKey @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "daily_minutes_allowed") val dailyMinutesAllowed: Int,
    @ColumnInfo(name = "is_strictly_blocked") val isStrictlyBlocked: Boolean = false,
    // El valor por defecto es el de las filas que ya existían antes de la migración 3→4 (todas eran flexibles).
    @ColumnInfo(name = "extensions_per_day", defaultValue = "3") val extensionsPerDay: Int = 3,
    // Las filas anteriores a la migración 6→7 no eran de ocio.
    @ColumnInfo(name = "is_leisure", defaultValue = "0") val isLeisure: Boolean = false,
)
