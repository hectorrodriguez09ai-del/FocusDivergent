package com.focusdivergent.app.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Icono de una app instalada. Se carga fuera del hilo principal y, mientras llega (o si la app ya no está
 * instalada), se ve un cuadro del color de las tarjetas, sin saltos en la lista.
 *
 * Es decorativo para los lectores de pantalla: el nombre de la app va siempre al lado, como texto.
 */
@Composable
fun AppIcon(
    packageName: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(1)
    val icon: ImageBitmap? by produceState<ImageBitmap?>(initialValue = null, packageName, sizePx) {
        value = withContext(Dispatchers.Default) {
            try {
                context.packageManager.getApplicationIcon(packageName).toBitmap(sizePx, sizePx).asImageBitmap()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Desinstalada o sin icono legible: se queda el cuadro neutro.
                null
            }
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        icon?.let { Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(size)) }
    }
}
