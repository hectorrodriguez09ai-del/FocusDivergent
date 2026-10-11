package com.focusdivergent.app.di

import com.focusdivergent.app.data.ai.ChatCompletionsSuggester
import com.focusdivergent.app.data.ai.HttpPoster
import com.focusdivergent.app.data.ai.KeystoreSecretCipher
import com.focusdivergent.app.data.ai.SecretCipher
import com.focusdivergent.app.data.ai.UrlConnectionPoster
import com.focusdivergent.app.data.repository.AppLimitRepositoryImpl
import com.focusdivergent.app.data.settings.SharedPreferencesLlmSettings
import com.focusdivergent.app.domain.ai.LlmSettings
import com.focusdivergent.app.domain.ai.StepSuggester
import com.focusdivergent.app.data.repository.BrainDumpRepositoryImpl
import com.focusdivergent.app.data.repository.CosmeticRepositoryImpl
import com.focusdivergent.app.data.repository.PendingLimitChangeRepositoryImpl
import com.focusdivergent.app.data.repository.RewardRepositoryImpl
import com.focusdivergent.app.data.repository.RoutineCompletionRepositoryImpl
import com.focusdivergent.app.data.repository.StepTemplateRepositoryImpl
import com.focusdivergent.app.data.repository.TaskRepositoryImpl
import com.focusdivergent.app.data.repository.TaskStepRepositoryImpl
import com.focusdivergent.app.data.repository.TimeExtensionRepositoryImpl
import com.focusdivergent.app.data.repository.UserStatsRepositoryImpl
import com.focusdivergent.app.data.repository.VaultRepositoryImpl
import com.focusdivergent.app.data.usage.UsageStatsRepository
import com.focusdivergent.app.domain.repository.AppLimitRepository
import com.focusdivergent.app.domain.repository.BrainDumpRepository
import com.focusdivergent.app.domain.repository.CosmeticRepository
import com.focusdivergent.app.domain.repository.PendingLimitChangeRepository
import com.focusdivergent.app.domain.repository.RewardRepository
import com.focusdivergent.app.domain.repository.RoutineCompletionRepository
import com.focusdivergent.app.domain.repository.StepTemplateRepository
import com.focusdivergent.app.domain.repository.TaskRepository
import com.focusdivergent.app.domain.repository.TaskStepRepository
import com.focusdivergent.app.domain.repository.TimeExtensionRepository
import com.focusdivergent.app.domain.repository.UsageRepository
import com.focusdivergent.app.domain.repository.UserStatsRepository
import com.focusdivergent.app.domain.repository.VaultRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Enlaza cada interfaz de repositorio del dominio con su implementación sobre Room. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindTaskRepository(impl: TaskRepositoryImpl): TaskRepository

    @Binds
    abstract fun bindAppLimitRepository(impl: AppLimitRepositoryImpl): AppLimitRepository

    @Binds
    abstract fun bindBrainDumpRepository(impl: BrainDumpRepositoryImpl): BrainDumpRepository

    @Binds
    abstract fun bindVaultRepository(impl: VaultRepositoryImpl): VaultRepository

    @Binds
    abstract fun bindUserStatsRepository(impl: UserStatsRepositoryImpl): UserStatsRepository

    @Binds
    abstract fun bindUsageRepository(impl: UsageStatsRepository): UsageRepository

    @Binds
    abstract fun bindTimeExtensionRepository(impl: TimeExtensionRepositoryImpl): TimeExtensionRepository

    @Binds
    abstract fun bindRoutineCompletionRepository(impl: RoutineCompletionRepositoryImpl): RoutineCompletionRepository

    @Binds
    abstract fun bindPendingLimitChangeRepository(impl: PendingLimitChangeRepositoryImpl): PendingLimitChangeRepository

    @Binds
    abstract fun bindRewardRepository(impl: RewardRepositoryImpl): RewardRepository

    @Binds
    abstract fun bindCosmeticRepository(impl: CosmeticRepositoryImpl): CosmeticRepository

    @Binds
    abstract fun bindLlmSettings(impl: SharedPreferencesLlmSettings): LlmSettings

    @Binds
    abstract fun bindSecretCipher(impl: KeystoreSecretCipher): SecretCipher

    @Binds
    abstract fun bindHttpPoster(impl: UrlConnectionPoster): HttpPoster

    @Binds
    abstract fun bindStepSuggester(impl: ChatCompletionsSuggester): StepSuggester

    @Binds
    abstract fun bindTaskStepRepository(impl: TaskStepRepositoryImpl): TaskStepRepository

    @Binds
    abstract fun bindStepTemplateRepository(impl: StepTemplateRepositoryImpl): StepTemplateRepository
}
