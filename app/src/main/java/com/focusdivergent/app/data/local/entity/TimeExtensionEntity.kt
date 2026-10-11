package com.focusdivergent.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Una ampliación de tiempo ganada resolviendo el reto matemático (RF-1.3): una fila por ampliación.
 *
 * No tiene clave foránea hacia `app_limits` a propósito: si el usuario borra y vuelve a crear el límite
 * de una app, las ampliaciones de hoy siguen contando y el tope diario no se reinicia por esa vía.
 */
@Entity(
    tableName = "time_extensions",
    indices = [Index(value = ["package_name", "granted_at"])],
)
data class TimeExtensionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "granted_at") val grantedAt: Long,
    val minutes: Int,
)
