package com.focusdivergent.app.domain.repository

import com.focusdivergent.app.domain.reward.CosmeticPurchase
import kotlinx.coroutines.flow.Flow

/** Los cosméticos que el usuario ya ha comprado. Comprar no se deshace. */
interface CosmeticRepository {

    fun observePurchases(): Flow<List<CosmeticPurchase>>

    /** Guarda la compra. Si ya estaba comprado, no hace nada. */
    suspend fun add(purchase: CosmeticPurchase)
}
