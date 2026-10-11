package com.focusdivergent.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focusdivergent.app.data.local.FocusDatabase

/**
 * Base de datos Room en memoria para los tests con Robolectric.
 *
 * Con [seed] a `false` no se crea la fila única de `user_stats`, para probar el caso de arranque en vacío.
 */
internal fun createTestDatabase(seed: Boolean = true): FocusDatabase {
    val builder = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        FocusDatabase::class.java,
    ).allowMainThreadQueries()
    if (seed) builder.addCallback(FocusDatabase.SeedCallback)
    return builder.build()
}
