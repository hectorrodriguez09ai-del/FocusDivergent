package com.focusdivergent.app.domain.settings

/** Recuerda si el usuario ya pasó por la bienvenida, para no enseñársela en cada arranque. */
interface OnboardingState {

    val isDone: Boolean

    fun markDone()
}
