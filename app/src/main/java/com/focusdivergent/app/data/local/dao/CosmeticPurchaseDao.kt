package com.focusdivergent.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.focusdivergent.app.data.local.entity.CosmeticPurchaseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CosmeticPurchaseDao {

    @Query("SELECT * FROM cosmetic_purchases")
    fun observeAll(): Flow<List<CosmeticPurchaseEntity>>

    /** Comprar dos veces lo mismo no cobra dos veces. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(purchase: CosmeticPurchaseEntity)
}
