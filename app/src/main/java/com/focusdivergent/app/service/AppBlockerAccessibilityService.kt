package com.focusdivergent.app.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Servicio de accesibilidad que avisa en el acto de que ha cambiado la ventana en pantalla.
 *
 * No decide nada ni lee contenido (`canRetrieveWindowContent=false`): solo despierta al servicio de
 * bloqueo, que es quien consulta el uso de apps y decide si hay que cubrir la app.
 */
@AndroidEntryPoint
class AppBlockerAccessibilityService : AccessibilityService() {

    @Inject lateinit var trigger: BlockCheckTrigger

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) trigger.request()
    }

    override fun onInterrupt() = Unit
}
