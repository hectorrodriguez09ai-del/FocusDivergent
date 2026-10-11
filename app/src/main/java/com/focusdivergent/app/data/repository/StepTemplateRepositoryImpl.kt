package com.focusdivergent.app.data.repository

import com.focusdivergent.app.data.local.dao.StepTemplateDao
import com.focusdivergent.app.data.local.entity.StepTemplateEntity
import com.focusdivergent.app.domain.repository.StepTemplateRepository
import com.focusdivergent.app.domain.steps.StepTemplate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StepTemplateRepositoryImpl @Inject constructor(
    private val dao: StepTemplateDao,
) : StepTemplateRepository {

    override fun observeAll(): Flow<List<StepTemplate>> = dao.observeAll().map { rows ->
        rows.map { StepTemplate(id = it.id, name = it.name, steps = it.steps.lines().filter(String::isNotBlank)) }
    }

    override suspend fun save(name: String, steps: List<String>) {
        val joined = steps.joinToString("\n")
        val existing = dao.getAll().firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (existing != null) dao.update(existing.id, name, joined) else dao.insert(StepTemplateEntity(name = name, steps = joined))
    }

    override suspend fun delete(id: Long) = dao.delete(id)
}
