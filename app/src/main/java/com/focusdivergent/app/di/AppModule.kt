package com.focusdivergent.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import kotlin.random.Random
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /** Reloj del sistema; los tests inyectan uno fijo para controlar «qué es hoy». */
    @Provides
    fun provideClock(): Clock = Clock.systemDefaultZone()

    /** Azar de la app («Elige por mí»); los tests inyectan uno con semilla para que el resultado sea fijo. */
    @Provides
    fun provideRandom(): Random = Random.Default

    /** Nombre de paquete de la propia app, para no bloquearla nunca. */
    @Provides
    @Named("ownPackage")
    fun provideOwnPackage(@ApplicationContext context: Context): String = context.packageName
}
