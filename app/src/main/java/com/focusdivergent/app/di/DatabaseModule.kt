package com.focusdivergent.app.di

import android.content.Context
import androidx.room.Room
import com.focusdivergent.app.data.local.FocusDatabase
import com.focusdivergent.app.data.local.dao.AppLimitDao
import com.focusdivergent.app.data.local.dao.BrainDumpDao
import com.focusdivergent.app.data.local.dao.CosmeticPurchaseDao
import com.focusdivergent.app.data.local.dao.PendingLimitChangeDao
import com.focusdivergent.app.data.local.dao.RoutineCompletionDao
import com.focusdivergent.app.data.local.dao.StepTemplateDao
import com.focusdivergent.app.data.local.dao.TaskDao
import com.focusdivergent.app.data.local.dao.TaskStepDao
import com.focusdivergent.app.data.local.dao.TimeExtensionDao
import com.focusdivergent.app.data.local.dao.UserStatsDao
import com.focusdivergent.app.data.local.dao.VaultNodeDao
import com.focusdivergent.app.data.local.dao.XpEventDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Provee la base de datos (una sola instancia por proceso) y sus DAOs. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FocusDatabase =
        Room.databaseBuilder(context, FocusDatabase::class.java, FocusDatabase.NAME)
            .addCallback(FocusDatabase.SeedCallback)
            .build()

    @Provides
    fun provideTaskDao(db: FocusDatabase): TaskDao = db.taskDao()

    @Provides
    fun provideAppLimitDao(db: FocusDatabase): AppLimitDao = db.appLimitDao()

    @Provides
    fun provideBrainDumpDao(db: FocusDatabase): BrainDumpDao = db.brainDumpDao()

    @Provides
    fun provideVaultNodeDao(db: FocusDatabase): VaultNodeDao = db.vaultNodeDao()

    @Provides
    fun provideUserStatsDao(db: FocusDatabase): UserStatsDao = db.userStatsDao()

    @Provides
    fun provideTimeExtensionDao(db: FocusDatabase): TimeExtensionDao = db.timeExtensionDao()

    @Provides
    fun provideRoutineCompletionDao(db: FocusDatabase): RoutineCompletionDao = db.routineCompletionDao()

    @Provides
    fun providePendingLimitChangeDao(db: FocusDatabase): PendingLimitChangeDao = db.pendingLimitChangeDao()

    @Provides
    fun provideXpEventDao(db: FocusDatabase): XpEventDao = db.xpEventDao()

    @Provides
    fun provideCosmeticPurchaseDao(db: FocusDatabase): CosmeticPurchaseDao = db.cosmeticPurchaseDao()

    @Provides
    fun provideTaskStepDao(db: FocusDatabase): TaskStepDao = db.taskStepDao()

    @Provides
    fun provideStepTemplateDao(db: FocusDatabase): StepTemplateDao = db.stepTemplateDao()
}
