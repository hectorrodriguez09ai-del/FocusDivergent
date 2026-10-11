package com.focusdivergent.app.presentation.capture

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import com.focusdivergent.app.R
import com.focusdivergent.app.service.CaptureTileService

/**
 * Pide a Android que añada el mosaico «Anotar idea» a los Ajustes rápidos: el sistema enseña su propio diálogo y el
 * usuario decide. [onHandled] se llama cuando ya hay respuesta (lo añadió, ya lo tenía o dijo que no); si la
 * petición falla por otra causa no se llama, y la oferta sigue disponible.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun requestAddCaptureTile(context: Context, onHandled: () -> Unit) {
    val statusBar = context.getSystemService(StatusBarManager::class.java) ?: return
    statusBar.requestAddTileService(
        ComponentName(context, CaptureTileService::class.java),
        context.getString(R.string.tile_label),
        Icon.createWithResource(context, R.drawable.ic_idea),
        context.mainExecutor,
    ) { result ->
        // 0 = no lo añadió, 1 = ya estaba, 2 = añadido. Los errores son negativos.
        if (result in TILE_NOT_ADDED..TILE_ADDED) onHandled()
    }
}

private const val TILE_NOT_ADDED = StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED
private const val TILE_ADDED = StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED
