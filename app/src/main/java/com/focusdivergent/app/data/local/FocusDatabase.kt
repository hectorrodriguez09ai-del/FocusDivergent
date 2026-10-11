package com.focusdivergent.app.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
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
import com.focusdivergent.app.data.local.entity.AppLimitEntity
import com.focusdivergent.app.data.local.entity.BrainDumpEntity
import com.focusdivergent.app.data.local.entity.CosmeticPurchaseEntity
import com.focusdivergent.app.data.local.entity.PendingLimitChangeEntity
import com.focusdivergent.app.data.local.entity.RoutineCompletionEntity
import com.focusdivergent.app.data.local.entity.StepTemplateEntity
import com.focusdivergent.app.data.local.entity.TaskEntity
import com.focusdivergent.app.data.local.entity.TaskStepEntity
import com.focusdivergent.app.data.local.entity.TimeExtensionEntity
import com.focusdivergent.app.data.local.entity.UserStatsEntity
import com.focusdivergent.app.data.local.entity.VaultNodeEntity
import com.focusdivergent.app.data.local.entity.VaultNodeFtsEntity
import com.focusdivergent.app.data.local.entity.XpEventEntity

/**
 * Base de datos local de FocusDivergent. Es la única fuente de verdad: nada sale del dispositivo (RNF-1).
 *
 * Al cambiar el esquema hay que subir [version] y añadir una migración; el JSON de cada versión
 * se exporta a `app/schemas/`.
 *
 * - v1: esquema inicial.
 * - v2: tabla `time_extensions` (ampliaciones de tiempo ganadas con el reto matemático).
 * - v3: `tasks.estimated_minutes` y `tasks.completed_at`, y tabla `routine_completions` (rutinas hechas por día).
 * - v4: `app_limits.extensions_per_day` (nivel de fricción) y tabla `pending_limit_changes` (relajaciones a medianoche).
 * - v5: `vault_nodes.updated_at` (última vez que se tocó una nota o carpeta de la Bóveda).
 * - v6: libro de XP `xp_events`, compras de cosméticos `cosmetic_purchases` y `user_stats.best_streak` (en lugar de
 *   los contadores de XP, monedas y racha, que ahora se calculan).
 * - v7: `app_limits.is_leisure` y `pending_limit_changes.is_leisure` (apps «de ocio» que el Cierre de Jornada deja
 *   libres de noche) y `xp_events.minutes` (minutos de enfoque de cada movimiento, para el resumen del día).
 * - v8: micro-pasos de las tareas `task_steps` y plantillas de pasos del usuario `step_templates` (RF-3.1).
 */
@Database(
    entities = [
        TaskEntity::class,
        AppLimitEntity::class,
        BrainDumpEntity::class,
        VaultNodeEntity::class,
        VaultNodeFtsEntity::class,
        UserStatsEntity::class,
        TimeExtensionEntity::class,
        RoutineCompletionEntity::class,
        PendingLimitChangeEntity::class,
        XpEventEntity::class,
        CosmeticPurchaseEntity::class,
        TaskStepEntity::class,
        StepTemplateEntity::class,
    ],
    version = 8,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6, spec = Migration5To6::class),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
    ],
)
@TypeConverters(Converters::class)
abstract class FocusDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun appLimitDao(): AppLimitDao
    abstract fun brainDumpDao(): BrainDumpDao
    abstract fun vaultNodeDao(): VaultNodeDao
    abstract fun userStatsDao(): UserStatsDao
    abstract fun timeExtensionDao(): TimeExtensionDao
    abstract fun routineCompletionDao(): RoutineCompletionDao
    abstract fun pendingLimitChangeDao(): PendingLimitChangeDao
    abstract fun xpEventDao(): XpEventDao
    abstract fun cosmeticPurchaseDao(): CosmeticPurchaseDao
    abstract fun taskStepDao(): TaskStepDao
    abstract fun stepTemplateDao(): StepTemplateDao

    companion object {
        const val NAME = "focus_divergent.db"

        /** Crea la fila única de `user_stats` la primera vez que se crea la base de datos. */
        val SeedCallback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "INSERT OR IGNORE INTO user_stats (id, best_streak) VALUES (${UserStatsEntity.SINGLETON_ID}, 0)",
                )
            }
        }
    }
}
