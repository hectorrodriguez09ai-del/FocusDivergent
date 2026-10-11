package com.focusdivergent.app.data.local

import android.app.Application
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Comprueba cada migración de Room con los esquemas JSON exportados a `app/schemas/`. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FocusDatabaseMigrationTest {

    private val databaseFile: File = File.createTempFile("migration-test", ".db").also { it.deleteOnExit() }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        databaseFile,
        AndroidSQLiteDriver(),
        FocusDatabase::class,
        autoMigrationSpecs = listOf(Migration5To6()),
    )

    @Test
    fun `la migracion 1 a 2 conserva los datos y crea time_extensions`() {
        helper.createDatabase(1).use { db ->
            db.execSQL("INSERT INTO app_limits (package_name, daily_minutes_allowed, is_strictly_blocked) VALUES ('com.example.social', 30, 1)")
            db.execSQL("INSERT INTO tasks (title, type, energy_level, status) VALUES ('Regar plantas', 'TASK', 1, 'PENDING')")
            db.execSQL("INSERT INTO vault_nodes (title, markdown_content, tags) VALUES ('Escalas', 'Pentatónica', '#Musica')")
            db.execSQL("INSERT INTO user_stats (id, current_xp, focus_coins, streak_days) VALUES (1, 120, 8, 4)")
        }

        helper.runMigrationsAndValidate(2, emptyList<Migration>()).use { db ->
            assertEquals(listOf(30L, 1L), db.longs("SELECT daily_minutes_allowed, is_strictly_blocked FROM app_limits WHERE package_name = 'com.example.social'"))
            assertEquals("Regar plantas", db.text("SELECT title FROM tasks"))
            assertEquals(listOf(120L, 8L, 4L), db.longs("SELECT current_xp, focus_coins, streak_days FROM user_stats WHERE id = 1"))

            // La búsqueda de texto completo sobrevive a la migración, con su tokenizador sin acentos.
            assertEquals(
                "Escalas",
                db.text("SELECT vault_nodes.title FROM vault_nodes JOIN vault_nodes_fts ON vault_nodes.id = vault_nodes_fts.rowid WHERE vault_nodes_fts MATCH 'pentatonica'"),
            )

            // La tabla nueva existe, está vacía y acepta filas.
            assertEquals(listOf(0L), db.longs("SELECT COUNT(*) FROM time_extensions"))
            db.execSQL("INSERT INTO time_extensions (package_name, granted_at, minutes) VALUES ('com.example.social', 1000, 15)")
            assertEquals(listOf(15L), db.longs("SELECT minutes FROM time_extensions"))
        }
    }

    @Test
    fun `la migracion 2 a 3 conserva las actividades y anade duracion, fecha de completado y rutinas por dia`() {
        helper.createDatabase(2).use { db ->
            db.execSQL("INSERT INTO tasks (id, title, type, energy_level, status, start_time, recurrence_rule) VALUES (1, 'Cita médica', 'OBLIGATION', 2, 'PENDING', 1791525331019, NULL)")
            db.execSQL("INSERT INTO tasks (id, title, type, energy_level, status) VALUES (2, 'Medicación', 'ROUTINE', 1, 'DONE')")
            db.execSQL("INSERT INTO app_limits (package_name, daily_minutes_allowed, is_strictly_blocked) VALUES ('com.example.social', 30, 0)")
            db.execSQL("INSERT INTO time_extensions (package_name, granted_at, minutes) VALUES ('com.example.social', 1000, 15)")
        }

        helper.runMigrationsAndValidate(3, emptyList<Migration>()).use { db ->
            // Lo que había sigue igual.
            assertEquals("Cita médica", db.text("SELECT title FROM tasks WHERE id = 1"))
            assertEquals(listOf(1791525331019L), db.longs("SELECT start_time FROM tasks WHERE id = 1"))
            assertEquals("DONE", db.text("SELECT status FROM tasks WHERE id = 2"))
            assertEquals(listOf(30L), db.longs("SELECT daily_minutes_allowed FROM app_limits"))
            assertEquals(listOf(15L), db.longs("SELECT minutes FROM time_extensions"))

            // Las columnas nuevas nacen vacías en las filas antiguas y aceptan valores.
            assertEquals(listOf(2L), db.longs("SELECT COUNT(*) FROM tasks WHERE estimated_minutes IS NULL AND completed_at IS NULL"))
            db.execSQL("UPDATE tasks SET estimated_minutes = 45, completed_at = 2000 WHERE id = 1")
            assertEquals(listOf(45L, 2000L), db.longs("SELECT estimated_minutes, completed_at FROM tasks WHERE id = 1"))

            // La tabla de rutinas por día existe, no admite duplicados y se vacía al borrar la rutina.
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("INSERT INTO routine_completions (task_id, day, completed_at) VALUES (2, 20736, 3000)")
            db.execSQL("INSERT OR IGNORE INTO routine_completions (task_id, day, completed_at) VALUES (2, 20736, 9999)")
            db.execSQL("INSERT INTO routine_completions (task_id, day, completed_at) VALUES (2, 20737, 4000)")
            assertEquals(listOf(2L), db.longs("SELECT COUNT(*) FROM routine_completions"))
            assertEquals(listOf(3000L), db.longs("SELECT completed_at FROM routine_completions WHERE day = 20736"))
            db.execSQL("DELETE FROM tasks WHERE id = 2")
            assertEquals(listOf(0L), db.longs("SELECT COUNT(*) FROM routine_completions"))
        }
    }

    @Test
    fun `la migracion 3 a 4 deja los limites existentes como flexibles y crea los cambios pendientes`() {
        helper.createDatabase(3).use { db ->
            db.execSQL("INSERT INTO app_limits (package_name, daily_minutes_allowed, is_strictly_blocked) VALUES ('com.example.social', 30, 0)")
            db.execSQL("INSERT INTO app_limits (package_name, daily_minutes_allowed, is_strictly_blocked) VALUES ('com.example.video', 60, 1)")
            db.execSQL("INSERT INTO tasks (id, title, type, energy_level, status, estimated_minutes) VALUES (1, 'Informe', 'TASK', 3, 'PENDING', 45)")
            db.execSQL("INSERT INTO time_extensions (package_name, granted_at, minutes) VALUES ('com.example.social', 1000, 15)")
        }

        helper.runMigrationsAndValidate(4, emptyList<Migration>()).use { db ->
            // Los límites de antes eran todos «con ampliaciones»: conservan el tope de 3 y todo lo demás.
            assertEquals(listOf(30L, 0L, 3L), db.longs("SELECT daily_minutes_allowed, is_strictly_blocked, extensions_per_day FROM app_limits WHERE package_name = 'com.example.social'"))
            assertEquals(listOf(60L, 1L, 3L), db.longs("SELECT daily_minutes_allowed, is_strictly_blocked, extensions_per_day FROM app_limits WHERE package_name = 'com.example.video'"))
            assertEquals(listOf(45L), db.longs("SELECT estimated_minutes FROM tasks WHERE id = 1"))
            assertEquals(listOf(15L), db.longs("SELECT minutes FROM time_extensions"))

            // La columna nueva acepta el nivel firme (0).
            db.execSQL("UPDATE app_limits SET extensions_per_day = 0 WHERE package_name = 'com.example.social'")
            assertEquals(listOf(0L), db.longs("SELECT extensions_per_day FROM app_limits WHERE package_name = 'com.example.social'"))

            // La tabla de cambios pendientes existe, admite uno por app y no está atada a app_limits.
            assertEquals(listOf(0L), db.longs("SELECT COUNT(*) FROM pending_limit_changes"))
            db.execSQL("INSERT INTO pending_limit_changes (package_name, daily_minutes_allowed, is_strictly_blocked, extensions_per_day, remove, applies_at) VALUES ('com.example.social', 60, 0, 3, 0, 5000)")
            db.execSQL("INSERT INTO pending_limit_changes (package_name, daily_minutes_allowed, is_strictly_blocked, extensions_per_day, remove, applies_at) VALUES ('com.example.sin.limite', 0, 0, 0, 1, 5000)")
            assertEquals(listOf(2L), db.longs("SELECT COUNT(*) FROM pending_limit_changes"))
        }
    }

    @Test
    fun `la migracion 4 a 5 conserva la Boveda, deja la fecha de las notas antiguas a cero y mantiene la busqueda`() {
        helper.createDatabase(4).use { db ->
            db.execSQL("INSERT INTO vault_nodes (id, parent_id, title, markdown_content, tags) VALUES (1, NULL, 'Música', NULL, '')")
            db.execSQL("INSERT INTO vault_nodes (id, parent_id, title, markdown_content, tags) VALUES (2, 1, 'Escalas', 'Pentatónica menor', '#teoria')")
        }

        helper.runMigrationsAndValidate(5, emptyList<Migration>()).use { db ->
            assertEquals(listOf(0L), db.longs("SELECT updated_at FROM vault_nodes WHERE id = 2"))
            assertEquals("Escalas", db.text("SELECT title FROM vault_nodes WHERE parent_id = 1"))

            // La columna nueva guarda una fecha y la búsqueda de texto completo sigue funcionando con las filas de antes y las nuevas.
            db.execSQL("INSERT INTO vault_nodes (id, parent_id, title, markdown_content, tags, updated_at) VALUES (3, NULL, 'Receta', 'pan de centeno', '', 1234)")
            assertEquals(listOf(1234L), db.longs("SELECT updated_at FROM vault_nodes WHERE id = 3"))
            assertEquals("Receta", db.text("SELECT vault_nodes.title FROM vault_nodes JOIN vault_nodes_fts ON vault_nodes.id = vault_nodes_fts.rowid WHERE vault_nodes_fts MATCH 'centeno'"))
            assertEquals("Escalas", db.text("SELECT vault_nodes.title FROM vault_nodes JOIN vault_nodes_fts ON vault_nodes.id = vault_nodes_fts.rowid WHERE vault_nodes_fts MATCH 'pentatonica'"))
        }
    }

    @Test
    fun `la migracion 5 a 6 conserva lo que habia, cambia los contadores por la mejor racha y crea el libro de XP y las compras`() {
        helper.createDatabase(5).use { db ->
            db.execSQL("INSERT INTO user_stats (id, current_xp, focus_coins, streak_days) VALUES (1, 0, 0, 0)")
            db.execSQL("INSERT INTO tasks (id, title, type, energy_level, status) VALUES (1, 'Medicación', 'ROUTINE', 1, 'PENDING')")
            db.execSQL("INSERT INTO routine_completions (task_id, day, completed_at) VALUES (1, 20736, 3000)")
            db.execSQL("INSERT INTO routine_completions (task_id, day, completed_at) VALUES (1, 20737, 4000)")
            // 20736 es el 10 de octubre de 2026; a mediodía UTC es ese mismo día en cualquier zona razonable.
            db.execSQL("INSERT INTO tasks (id, title, type, energy_level, status, completed_at) VALUES (2, 'Informe', 'TASK', 3, 'DONE', 1791633600000)")
            db.execSQL("INSERT INTO tasks (id, title, type, energy_level, status, completed_at) VALUES (3, 'Compra', 'TASK', 1, 'DONE', 1791720000000)")
            db.execSQL("INSERT INTO tasks (id, title, type, energy_level, status, completed_at) VALUES (4, 'Cita', 'OBLIGATION', 2, 'DONE', 1791633600000)")
            db.execSQL("INSERT INTO tasks (id, title, type, energy_level, status) VALUES (5, 'Pendiente', 'TASK', 2, 'PENDING')")
            db.execSQL("INSERT INTO tasks (id, title, type, energy_level, status) VALUES (6, 'Hecha sin fecha', 'TASK', 2, 'DONE')")
        }

        helper.runMigrationsAndValidate(6, listOf<Migration>()).use { db ->
            // La fila única sigue, ahora con la mejor racha en cero, y lo demás está intacto.
            assertEquals(listOf(1L, 0L), db.longs("SELECT id, best_streak FROM user_stats"))
            assertEquals("Medicación", db.text("SELECT title FROM tasks WHERE id = 1"))
            assertEquals(listOf(2L), db.longs("SELECT COUNT(*) FROM routine_completions"))

            // Lo ya hecho cuenta: tareas y Obligaciones terminadas (15 XP si pedían energía alta) y cada rutina marcada.
            assertEquals(listOf(5L, 51L, 5L), db.longs("SELECT COUNT(*), SUM(xp), SUM(coins) FROM xp_events"))
            assertEquals(listOf(15L, 20736L), db.longs("SELECT xp, day FROM xp_events WHERE cause = 'TASK_DONE' AND ref_id = 2"))
            assertEquals(listOf(10L, 20737L), db.longs("SELECT xp, day FROM xp_events WHERE cause = 'TASK_DONE' AND ref_id = 3"))
            assertEquals(listOf(10L), db.longs("SELECT xp FROM xp_events WHERE cause = 'TASK_DONE' AND ref_id = 4"))
            assertEquals(listOf(2L), db.longs("SELECT COUNT(*) FROM xp_events WHERE cause = 'ROUTINE_DONE' AND ref_id = 1"))
            // Ni lo pendiente ni lo «hecho» sin fecha de cierre dan XP, ni la rutina como tarea.
            assertEquals(listOf(0L), db.longs("SELECT COUNT(*) FROM xp_events WHERE cause = 'TASK_DONE' AND ref_id IN (1, 5, 6)"))

            // Los contadores antiguos ya no existen.
            assertEquals(listOf(0L), db.longs("SELECT COUNT(*) FROM pragma_table_info('user_stats') WHERE name IN ('current_xp', 'focus_coins', 'streak_days')"))

            // El libro de XP existe y no admite el mismo movimiento dos veces.
            db.execSQL("INSERT INTO xp_events (cause, ref_id, day, xp, coins) VALUES ('TASK_DONE', 9, 20736, 10, 1)")
            db.execSQL("INSERT OR IGNORE INTO xp_events (cause, ref_id, day, xp, coins) VALUES ('TASK_DONE', 9, 20736, 10, 1)")
            db.execSQL("INSERT INTO xp_events (cause, ref_id, day, xp, coins) VALUES ('TASK_DONE', 9, 20737, 10, 1)")
            assertEquals(listOf(7L), db.longs("SELECT COUNT(*) FROM xp_events"))

            // Las compras admiten un cosmético una sola vez.
            db.execSQL("INSERT INTO cosmetic_purchases (cosmetic_id, price) VALUES ('PROP_PLANT', 5)")
            db.execSQL("INSERT OR IGNORE INTO cosmetic_purchases (cosmetic_id, price) VALUES ('PROP_PLANT', 99)")
            assertEquals(listOf(5L), db.longs("SELECT price FROM cosmetic_purchases"))

            // La mejor racha acepta valores.
            db.execSQL("UPDATE user_stats SET best_streak = 7 WHERE id = 1")
            assertEquals(listOf(7L), db.longs("SELECT best_streak FROM user_stats"))
        }
    }

    @Test
    fun `la migracion 6 a 7 deja las apps y los cambios pendientes sin la marca de ocio y los movimientos sin minutos`() {
        helper.createDatabase(6).use { db ->
            db.execSQL("INSERT INTO app_limits (package_name, daily_minutes_allowed, is_strictly_blocked, extensions_per_day) VALUES ('com.example.social', 30, 0, 3)")
            db.execSQL("INSERT INTO pending_limit_changes (package_name, daily_minutes_allowed, is_strictly_blocked, extensions_per_day, remove, applies_at) VALUES ('com.example.social', 60, 0, 3, 0, 5000)")
            db.execSQL("INSERT INTO xp_events (cause, ref_id, day, xp, coins) VALUES ('TASK_DONE', 1, 20736, 10, 1)")
        }

        helper.runMigrationsAndValidate(7, emptyList<Migration>()).use { db ->
            // Lo que había sigue igual y ninguna app era de ocio.
            assertEquals(listOf(30L, 0L), db.longs("SELECT daily_minutes_allowed, is_leisure FROM app_limits WHERE package_name = 'com.example.social'"))
            assertEquals(listOf(60L, 0L), db.longs("SELECT daily_minutes_allowed, is_leisure FROM pending_limit_changes"))
            assertEquals(listOf(10L, 1L, 0L), db.longs("SELECT xp, coins, minutes FROM xp_events"))

            // Las columnas nuevas aceptan valores.
            db.execSQL("UPDATE app_limits SET is_leisure = 1 WHERE package_name = 'com.example.social'")
            db.execSQL("UPDATE pending_limit_changes SET is_leisure = 1")
            db.execSQL("INSERT INTO xp_events (cause, ref_id, day, xp, coins, minutes) VALUES ('FOCUS_SESSION', 99, 20736, 15, 1, 45)")
            assertEquals(listOf(1L), db.longs("SELECT is_leisure FROM app_limits"))
            assertEquals(listOf(1L), db.longs("SELECT is_leisure FROM pending_limit_changes"))
            assertEquals(listOf(45L), db.longs("SELECT minutes FROM xp_events WHERE cause = 'FOCUS_SESSION'"))
        }
    }

    @Test
    fun `la migracion 7 a 8 conserva las tareas y crea los pasos y las plantillas, con los pasos ligados a su tarea`() {
        helper.createDatabase(7).use { db ->
            db.execSQL("INSERT INTO tasks (id, title, type, energy_level, status) VALUES (1, 'Informe', 'TASK', 2, 'PENDING')")
            db.execSQL("INSERT INTO xp_events (cause, ref_id, day, xp, coins, minutes) VALUES ('TASK_DONE', 1, 20736, 10, 1, 0)")
        }

        helper.runMigrationsAndValidate(8, emptyList<Migration>()).use { db ->
            assertEquals("Informe", db.text("SELECT title FROM tasks WHERE id = 1"))
            assertEquals(listOf(10L), db.longs("SELECT xp FROM xp_events"))
            assertEquals(listOf(0L), db.longs("SELECT COUNT(*) FROM task_steps"))
            assertEquals(listOf(0L), db.longs("SELECT COUNT(*) FROM step_templates"))

            // Las tablas nuevas aceptan filas, y los pasos se van con su tarea.
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("INSERT INTO task_steps (task_id, position, text, done) VALUES (1, 0, 'Abrir el documento', 0)")
            db.execSQL("INSERT INTO step_templates (name, steps) VALUES ('Hacer la compra', 'Lista')")
            assertEquals(listOf(1L, 1L), db.longs("SELECT COUNT(*), (SELECT COUNT(*) FROM step_templates) FROM task_steps"))
            db.execSQL("DELETE FROM tasks WHERE id = 1")
            assertEquals(listOf(0L), db.longs("SELECT COUNT(*) FROM task_steps"))
        }
    }

    @Test
    fun `una base de datos de la version 1 llega a la 8 pasando por todas las migraciones`() {
        helper.createDatabase(1).use { db ->
            db.execSQL("INSERT INTO tasks (title, type, energy_level, status) VALUES ('Regar plantas', 'TASK', 1, 'PENDING')")
            db.execSQL("INSERT INTO app_limits (package_name, daily_minutes_allowed, is_strictly_blocked) VALUES ('com.example.social', 30, 0)")
            db.execSQL("INSERT INTO user_stats (id, current_xp, focus_coins, streak_days) VALUES (1, 120, 8, 4)")
        }

        helper.runMigrationsAndValidate(8, emptyList<Migration>()).use { db ->
            assertEquals(listOf(1L, 0L), db.longs("SELECT id, best_streak FROM user_stats"))
            assertEquals(listOf(0L), db.longs("SELECT COUNT(*) FROM xp_events"))
            assertEquals(listOf(0L), db.longs("SELECT COUNT(*) FROM cosmetic_purchases"))
            assertEquals("Regar plantas", db.text("SELECT title FROM tasks"))
            assertEquals(listOf(30L, 3L), db.longs("SELECT daily_minutes_allowed, extensions_per_day FROM app_limits"))
            assertEquals(listOf(0L), db.longs("SELECT COUNT(*) FROM time_extensions"))
            assertEquals(listOf(0L), db.longs("SELECT COUNT(*) FROM routine_completions"))
            assertEquals(listOf(0L), db.longs("SELECT COUNT(*) FROM pending_limit_changes"))
            assertEquals(listOf(0L), db.longs("SELECT COUNT(*) FROM vault_nodes"))
        }
    }

    /** Ejecuta una consulta y devuelve las columnas de la primera fila como enteros. */
    private fun SQLiteConnection.longs(sql: String): List<Long> = prepare(sql).use { statement ->
        assertTrue("La consulta no devolvió filas: $sql", statement.step())
        List(statement.getColumnCount()) { statement.getLong(it) }
    }

    /** Ejecuta una consulta y devuelve la primera columna de la primera fila como texto. */
    private fun SQLiteConnection.text(sql: String): String = prepare(sql).use { statement ->
        assertTrue("La consulta no devolvió filas: $sql", statement.step())
        statement.getText(0)
    }
}
