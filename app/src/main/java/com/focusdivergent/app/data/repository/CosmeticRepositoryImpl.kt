package com.focusdivergent.app.data.repository

import com.focusdivergent.app.data.local.dao.CosmeticPurchaseDao
import com.focusdivergent.app.data.local.entity.CosmeticPurchaseEntity
import com.focusdivergent.app.domain.repository.CosmeticRepository
import com.focusdivergent.app.domain.reward.Cosmetic
import com.focusdivergent.app.domain.reward.CosmeticPurchase
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CosmeticRepositoryImpl @Inject constructor(
    private val dao: CosmeticPurchaseDao,
) : CosmeticRepository {

    override fun observePurchases(): Flow<List<CosmeticPurchase>> = dao.observeAll().map { rows ->
        // Un id que esta versión no conoce (de una versión futura) se ignora en vez de romper la lista.
        rows.mapNotNull { row ->
            Cosmetic.entries.firstOrNull { it.name == row.cosmeticId }?.let { CosmeticPurchase(it, row.price) }
        }
    }

    override suspend fun add(purchase: CosmeticPurchase) =
        dao.insert(CosmeticPurchaseEntity(cosmeticId = purchase.cosmetic.name, price = purchase.price))
}
