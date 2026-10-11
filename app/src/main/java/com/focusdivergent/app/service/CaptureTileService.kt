package com.focusdivergent.app.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.focusdivergent.app.presentation.capture.CaptureActivity

/**
 * Mosaico «Anotar idea» de los Ajustes rápidos (RF-5.1): un toque abre la captura encima de lo que haya en pantalla.
 * Es un botón, no un interruptor, así que siempre se ve apagado.
 */
class CaptureTileService : TileService() {

    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        val intent = CaptureActivity.intent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        } else {
            // Solo antes de Android 14: desde ahí la variante con Intent lanza una excepción, y arriba se usa la de PendingIntent.
            @Suppress("DEPRECATION")
            @SuppressLint("StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}
