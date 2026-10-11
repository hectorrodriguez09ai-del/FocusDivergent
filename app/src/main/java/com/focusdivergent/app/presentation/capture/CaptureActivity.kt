package com.focusdivergent.app.presentation.capture

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.focusdivergent.app.R
import com.focusdivergent.app.presentation.theme.FocusDivergentTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Captura rápida de una idea (RF-5.1) desde la notificación de la protección, el mosaico de Ajustes rápidos o la
 * pestaña Bóveda. Es una actividad aparte, translúcida y sin barra, para abrirse deprisa encima de lo que se esté
 * haciendo y devolver al usuario justo ahí al terminar.
 */
@AndroidEntryPoint
class CaptureActivity : ComponentActivity() {

    private val viewModel: CaptureViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FocusDivergentTheme {
                val text by viewModel.text.collectAsStateWithLifecycle()
                CaptureContent(text = text, onTextChange = viewModel::setText, onSubmit = viewModel::submit)
            }
        }
        lifecycleScope.launch {
            viewModel.finished.collect { result ->
                if (result.saved) {
                    // La pantalla se cierra al momento: una confirmación breve evita la duda de «¿se guardó?».
                    Toast.makeText(applicationContext, R.string.capture_saved, Toast.LENGTH_SHORT).show()
                }
                finish()
            }
        }
    }

    // Si el usuario sale por otra vía (botón de inicio, recientes, una llamada), lo escrito se guarda igualmente.
    // Un giro de pantalla también para la actividad, pero ahí no se sale: se recrea y se sigue escribiendo.
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) viewModel.submit()
    }

    companion object {
        /** Intención para abrir la captura desde fuera de la app (notificación, mosaico). */
        fun intent(context: Context): Intent =
            Intent(context, CaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
