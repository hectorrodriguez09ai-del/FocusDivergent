package com.focusdivergent.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.CallSuper

/**
 * Base para receptores con `@AndroidEntryPoint`.
 *
 * Hilt inyecta las dependencias en el `onReceive` de la clase que genera, y las subclases deben
 * llamar a `super.onReceive` primero. `BroadcastReceiver.onReceive` es abstracto y Kotlin no deja
 * invocarlo, así que esta clase aporta una implementación vacía a la que sí se puede llamar.
 */
abstract class HiltBroadcastReceiver : BroadcastReceiver() {

    @CallSuper
    override fun onReceive(context: Context, intent: Intent) = Unit
}
