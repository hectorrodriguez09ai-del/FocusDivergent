package com.focusdivergent.app.data.local

import androidx.room.DeleteColumn
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.execSQL

/**
 * 5 → 6: el XP y las monedas pasan a calcularse desde el libro `xp_events`, así que los contadores de
 * `user_stats` sobran (en todas las instalaciones valían cero: nada los usaba) y se retiran. La mejor racha es la
 * columna nueva `best_streak`.
 *
 * Para que lo ya hecho cuente, al terminar la migración se anotan en el libro las tareas terminadas y las rutinas
 * marcadas hasta ahora, con los valores de `RewardRules` y en el día (de la zona horaria del móvil) en que se
 * hicieron. Es una sola vez: el índice único de `xp_events` evita duplicados si se repitiera.
 */
@DeleteColumn.Entries(
    DeleteColumn(tableName = "user_stats", columnName = "current_xp"),
    DeleteColumn(tableName = "user_stats", columnName = "focus_coins"),
    DeleteColumn(tableName = "user_stats", columnName = "streak_days"),
)
class Migration5To6 : AutoMigrationSpec {

    override fun onPostMigrate(connection: SQLiteConnection) {
        BACKFILL.forEach { connection.execSQL(it) }
    }

    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        BACKFILL.forEach { db.execSQL(it) }
    }

    private companion object {
        /** Día local (días desde 1970) de un instante en milisegundos. */
        const val LOCAL_DAY = "CAST(strftime('%s', completed_at / 1000, 'unixepoch', 'localtime') / 86400 AS INTEGER)"

        val BACKFILL = listOf(
            // Tareas y Obligaciones terminadas: 10 XP, 15 si pedían energía alta (3), y una moneda.
            "INSERT OR IGNORE INTO xp_events (cause, ref_id, day, xp, coins) " +
                "SELECT 'TASK_DONE', id, $LOCAL_DAY, CASE energy_level WHEN 3 THEN 15 ELSE 10 END, 1 " +
                "FROM tasks WHERE status = 'DONE' AND completed_at IS NOT NULL AND type <> 'ROUTINE'",
            // Rutinas ya marcadas, cada una en su día: 8 XP y una moneda.
            "INSERT OR IGNORE INTO xp_events (cause, ref_id, day, xp, coins) " +
                "SELECT 'ROUTINE_DONE', task_id, day, 8, 1 FROM routine_completions",
        )
    }
}
