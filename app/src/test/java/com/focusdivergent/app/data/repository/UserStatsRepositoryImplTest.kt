package com.focusdivergent.app.data.repository

import android.app.Application
import com.focusdivergent.app.domain.model.UserStats
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class UserStatsRepositoryImplTest {

    @Test
    fun `sin fila en la base de datos emite los valores iniciales`() = runTest {
        val db = createTestDatabase(seed = false)
        try {
            val repository = UserStatsRepositoryImpl(db.userStatsDao())

            assertEquals(UserStats(), repository.observe().first())
        } finally {
            db.close()
        }
    }

    @Test
    fun `la mejor racha sube pero nunca baja`() = runTest {
        val db = createTestDatabase()
        try {
            val repository = UserStatsRepositoryImpl(db.userStatsDao())

            repository.raiseBestStreak(3)
            assertEquals(UserStats(bestStreak = 3), repository.observe().first())

            repository.raiseBestStreak(7)
            repository.raiseBestStreak(5)
            repository.raiseBestStreak(0)

            assertEquals(UserStats(bestStreak = 7), repository.observe().first())
        } finally {
            db.close()
        }
    }
}