package com.focusdivergent.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Un cosmético comprado con monedas de enfoque. El precio se guarda tal como se pagó. */
@Entity(tableName = "cosmetic_purchases")
data class CosmeticPurchaseEntity(
    @PrimaryKey @ColumnInfo(name = "cosmetic_id") val cosmeticId: String,
    val price: Int,
)
