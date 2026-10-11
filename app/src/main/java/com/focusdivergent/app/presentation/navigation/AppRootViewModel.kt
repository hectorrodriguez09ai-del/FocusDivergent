package com.focusdivergent.app.presentation.navigation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusdivergent.app.domain.settings.OnboardingState
import com.focusdivergent.app.domain.usecase.RunDayClosureUseCase
import com.focusdivergent.app.domain.usecase.ScheduleDayClosureUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Decide dónde arranca la app: en la bienvenida la primera vez, en «Hoy» las siguientes. */
@HiltViewModel
class AppRootViewModel @Inject constructor(
    private val onboarding: OnboardingState,
    private val runDayClosure: RunDayClosureUseCase,
    private val scheduleDayClosure: ScheduleDayClosureUseCase,
) : ViewModel() {

    /** Se lee una sola vez al crear la navegación: el destino inicial no cambia después. */
    val onboardingDone: Boolean = onboarding.isDone

    fun markOnboardingDone() = onboarding.markDone()

    /**
     * Al abrir la app se comprueba si el Cierre de Jornada de esta noche quedó sin hacer (alarma retrasada o móvil
     * apagado a esa hora, y servicio de bloqueo parado). Si ya está hecho, no hace nada.
     */
    fun catchUpClosure() {
        if (!onboardingDone) return
        viewModelScope.launch {
            try {
                if (runDayClosure() != null) scheduleDayClosure()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo cerrar la jornada al abrir la app", e)
            }
        }
    }

    private companion object {
        const val TAG = "AppRoot"
    }
}
