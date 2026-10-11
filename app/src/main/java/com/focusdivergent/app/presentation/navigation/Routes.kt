package com.focusdivergent.app.presentation.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.focusdivergent.app.R
import kotlinx.serialization.Serializable

// Rutas tipadas: cada destino es un tipo y sus argumentos son propiedades, así que un destino mal escrito o un
// argumento que falta es un error de compilación y no un cierre en tiempo de ejecución.

@Serializable
data object TodayRoute

/** «Elige por mí»: una sola propuesta para quien no sabe por dónde empezar. */
@Serializable
data object ChooseForMeRoute

/** Preparar la Lanzadera de 2 minutos. */
@Serializable
data object FocusLaunchRoute

@Serializable
data object TasksRoute

/** Sonido ambiente: ruido blanco, marrón, binaural o lluvia. */
@Serializable
data object AudioRoute

/**
 * El resumen del Cierre de Jornada.
 *
 * @property day el día del resumen, en días desde 1970; 0 = hoy.
 */
@Serializable
data class ClosureRoute(val day: Long = 0)

/** Ajustes del Cierre de Jornada: si está activo, a qué hora cierra y hasta cuándo quedan libres las apps de ocio. */
@Serializable
data object ClosureSettingsRoute

/** La mascota: nivel, etapas, racha y tienda de cosméticos. */
@Serializable
data object MascotRoute

/**
 * Temporizador visual y Pomodoro.
 *
 * @property taskId tarea a la que acompaña una sesión nueva; 0 = temporizador suelto.
 */
@Serializable
data class TimerRoute(val taskId: Long = 0)

/**
 * Crear o editar una actividad.
 *
 * @property taskId id de la actividad a editar; 0 para crear una nueva.
 * @property title título con el que arranca una actividad nueva (al convertir una idea en tarea).
 * @property ideaId idea del Brain Dump de la que viene; se marca como procesada al guardar la actividad. 0 = ninguna.
 */
@Serializable
data class TaskEditorRoute(val taskId: Long = 0, val title: String = "", val ideaId: Long = 0)

/** Ajustes de «Pasos con tu propia IA»: servicio, modelo y clave. */
@Serializable
data object AiSettingsRoute

/** Preparar los micro-pasos de una tarea: escribirlos, ordenarlos o partir de una plantilla. */
@Serializable
data class StepsRoute(val taskId: Long)

/** Los pasos de una tarea de uno en uno, a pantalla completa. */
@Serializable
data class StepFocusRoute(val taskId: Long)

@Serializable
data object VaultRoute

/**
 * Crear o editar una nota de la Bóveda.
 *
 * @property noteId id de la nota a editar; 0 para crear una nueva.
 * @property parentId carpeta donde se crea una nota nueva; 0 = raíz.
 */
@Serializable
data class NoteEditorRoute(val noteId: Long = 0, val parentId: Long = 0)

@Serializable
data object BlocksRoute

/** Elegir la app a la que poner un límite. */
@Serializable
data object AppPickerRoute

/**
 * Crear o cambiar el límite de una app.
 *
 * @property packageName paquete de la app; es el nombre del argumento que lee el ViewModel.
 */
@Serializable
data class LimitEditorRoute(val packageName: String)

/**
 * Permisos críticos, avisos puntuales y batería.
 *
 * @property onboarding `true` en el primer arranque: se muestra como bienvenida, sin «atrás» y con un botón
 * para entrar en la app. `false` cuando se abre desde la pestaña de bloqueos.
 */
@Serializable
data class PermissionsRoute(val onboarding: Boolean = false)

/** Las cuatro pestañas de la barra inferior (SRD §5), en el orden en que se ven. */
enum class TopLevelDestination(
    val route: Any,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    TODAY(TodayRoute, R.string.nav_today, R.drawable.ic_nav_today),
    TASKS(TasksRoute, R.string.nav_tasks, R.drawable.ic_nav_tasks),
    VAULT(VaultRoute, R.string.nav_vault, R.drawable.ic_nav_vault),
    BLOCKS(BlocksRoute, R.string.nav_blocks, R.drawable.ic_nav_blocks),
}
