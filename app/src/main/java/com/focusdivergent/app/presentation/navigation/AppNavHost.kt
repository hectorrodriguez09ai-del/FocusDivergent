package com.focusdivergent.app.presentation.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.focusdivergent.app.domain.braindump.IdeaText
import com.focusdivergent.app.presentation.ai.AiSettingsScreen
import com.focusdivergent.app.presentation.audio.AudioScreen
import com.focusdivergent.app.presentation.blocks.AppPickerScreen
import com.focusdivergent.app.presentation.blocks.BlocksScreen
import com.focusdivergent.app.presentation.blocks.LimitEditorScreen
import com.focusdivergent.app.presentation.chooseforme.ChooseForMeScreen
import com.focusdivergent.app.presentation.closure.ClosureSettingsScreen
import com.focusdivergent.app.presentation.closure.ClosureSummaryScreen
import com.focusdivergent.app.presentation.launch.FocusLaunchScreen
import com.focusdivergent.app.presentation.mascot.MascotScreen
import com.focusdivergent.app.presentation.permissions.PermissionsScreen
import com.focusdivergent.app.presentation.steps.StepFocusScreen
import com.focusdivergent.app.presentation.steps.StepsEditorScreen
import com.focusdivergent.app.presentation.tasks.TaskEditorScreen
import com.focusdivergent.app.presentation.tasks.TasksScreen
import com.focusdivergent.app.presentation.theme.LocalReducedMotion
import com.focusdivergent.app.presentation.timer.TimerScreen
import com.focusdivergent.app.presentation.theme.Motion
import com.focusdivergent.app.presentation.today.TodayScreen
import com.focusdivergent.app.presentation.vault.NoteEditorScreen
import com.focusdivergent.app.presentation.vault.VaultScreen

/**
 * Raíz de la interfaz: un único grafo de navegación con la barra inferior encima.
 *
 * La barra solo se ve en las cuatro pestañas; el resto de pantallas (bienvenida, editores) ocupan todo el alto
 * para que no haya nada que distraiga de la tarea que se está haciendo.
 */
@Composable
fun AppNavHost(
    openTimer: Boolean = false,
    openAudio: Boolean = false,
    openClosureDay: Long? = null,
    viewModel: AppRootViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTab = TopLevelDestination.entries.firstOrNull { tab ->
        backStackEntry?.destination?.hasRoute(tab.route::class) == true
    }

    // Las transiciones son un fundido corto; con el movimiento reducido del sistema, ninguna.
    val transitionMillis = if (LocalReducedMotion.current) 0 else Motion.MEDIUM_MS
    val startDestination: Any = remember {
        if (viewModel.onboardingDone) TodayRoute else PermissionsRoute(onboarding = true)
    }

    // Las notificaciones del temporizador abren la app directamente en él (si ya pasó la bienvenida).
    LaunchedEffect(openTimer) {
        if (openTimer && viewModel.onboardingDone) navController.navigate(TimerRoute())
    }
    LaunchedEffect(openAudio) {
        if (openAudio && viewModel.onboardingDone) navController.navigate(AudioRoute)
    }
    // La notificación del Cierre de Jornada abre el resumen de su día.
    LaunchedEffect(openClosureDay) {
        if (openClosureDay != null && viewModel.onboardingDone) navController.navigate(ClosureRoute(openClosureDay))
    }
    LifecycleResumeEffect(Unit) {
        viewModel.catchUpClosure()
        onPauseOrDispose { }
    }

    Scaffold(
        // Cada pantalla gestiona sus propios márgenes del sistema con su barra superior; aquí solo se reserva
        // el hueco de la barra inferior.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (currentTab != null) {
                MainBottomBar(current = currentTab, onSelect = navController::navigateToTab)
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            enterTransition = { fadeIn(tween(transitionMillis)) },
            exitTransition = { fadeOut(tween(transitionMillis)) },
            popEnterTransition = { fadeIn(tween(transitionMillis)) },
            popExitTransition = { fadeOut(tween(transitionMillis)) },
        ) {
            composable<TodayRoute> {
                TodayScreen(
                    onChooseForMe = { navController.navigate(ChooseForMeRoute) },
                    onLaunch = { navController.navigate(FocusLaunchRoute) },
                    onEditTask = { id -> navController.navigate(TaskEditorRoute(taskId = id)) },
                    onNewTask = { navController.navigate(TaskEditorRoute()) },
                    onSeeAllTasks = { navController.navigateToTab(TopLevelDestination.TASKS) },
                    onTimer = { navController.navigate(TimerRoute()) },
                    onAudio = { navController.navigate(AudioRoute) },
                    onMascot = { navController.navigate(MascotRoute) },
                    onOpenSteps = { id -> navController.navigate(StepFocusRoute(id)) },
                    onSplitSteps = { id -> navController.navigate(StepsRoute(id)) },
                )
            }
            composable<MascotRoute> { MascotScreen(onBack = navController::popBackStack) }
            composable<ClosureRoute> {
                ClosureSummaryScreen(
                    onClose = navController::popBackStack,
                    onReviewInbox = { navController.navigateToTab(TopLevelDestination.VAULT) },
                )
            }
            composable<ClosureSettingsRoute> {
                ClosureSettingsScreen(
                    onBack = navController::popBackStack,
                    onOpenSummary = { day -> navController.navigate(ClosureRoute(day)) },
                )
            }
            composable<AudioRoute> { AudioScreen(onBack = navController::popBackStack) }
            composable<TimerRoute> { TimerScreen(onBack = navController::popBackStack, onAudio = { navController.navigate(AudioRoute) }) }
            composable<FocusLaunchRoute> {
                FocusLaunchScreen(
                    onBack = navController::popBackStack,
                    // Al empezar se vuelve a «Hoy»: el bloqueo cubre todo el móvil desde ese momento.
                    onStarted = navController::popBackStack,
                    onOpenPermissions = { navController.navigate(PermissionsRoute()) },
                )
            }
            composable<ChooseForMeRoute> {
                ChooseForMeScreen(
                    onBack = navController::popBackStack,
                    onStarted = navController::popBackStack,
                    onSeeList = { navController.navigateToTab(TopLevelDestination.TASKS) },
                    onCreateTask = {
                        // El selector no se queda en la pila: al guardar la tarea se vuelve a «Hoy».
                        navController.navigate(TaskEditorRoute()) { popUpTo<ChooseForMeRoute> { inclusive = true } }
                    },
                )
            }
            composable<TasksRoute> {
                TasksScreen(
                    onNewTask = { navController.navigate(TaskEditorRoute()) },
                    onEditTask = { id -> navController.navigate(TaskEditorRoute(taskId = id)) },
                )
            }
            composable<TaskEditorRoute> {
                TaskEditorScreen(
                    onClose = navController::popBackStack,
                    // El editor se queda debajo, con lo que se hubiera escrito: al volver del temporizador sigue ahí.
                    onStartTimer = { id -> navController.navigate(TimerRoute(taskId = id)) },
                    onOpenSteps = { id -> navController.navigate(StepsRoute(id)) },
                )
            }
            composable<StepsRoute> { entry ->
                val taskId = entry.toRoute<StepsRoute>().taskId
                StepsEditorScreen(
                    taskId = taskId,
                    onBack = navController::popBackStack,
                    // Los pasos se preparan encima del editor; al empezar se pasa a verlos de uno en uno.
                    onStart = { id -> navController.navigate(StepFocusRoute(id)) { popUpTo<StepsRoute> { inclusive = true } } },
                )
            }
            composable<StepFocusRoute> { entry ->
                val taskId = entry.toRoute<StepFocusRoute>().taskId
                StepFocusScreen(
                    onBack = navController::popBackStack,
                    onEditSteps = { navController.navigate(StepsRoute(taskId)) { popUpTo<StepFocusRoute> { inclusive = true } } },
                )
            }
            composable<VaultRoute> {
                VaultScreen(
                    onMakeTask = { idea ->
                        navController.navigate(TaskEditorRoute(title = IdeaText.titleOf(idea.content), ideaId = idea.id))
                    },
                    onOpenNote = { id -> navController.navigate(NoteEditorRoute(noteId = id)) },
                    onNewNote = { parentId -> navController.navigate(NoteEditorRoute(parentId = parentId ?: 0)) },
                )
            }
            composable<NoteEditorRoute> { NoteEditorScreen(onClose = navController::popBackStack) }
            composable<BlocksRoute> {
                BlocksScreen(
                    onAddApp = { navController.navigate(AppPickerRoute) },
                    onEditLimit = { packageName -> navController.navigate(LimitEditorRoute(packageName)) },
                    onOpenPermissions = { navController.navigate(PermissionsRoute()) },
                    onOpenClosure = { navController.navigate(ClosureSettingsRoute) },
                    onOpenAi = { navController.navigate(AiSettingsRoute) },
                )
            }
            composable<AiSettingsRoute> { AiSettingsScreen(onBack = navController::popBackStack) }
            composable<AppPickerRoute> {
                AppPickerScreen(
                    onBack = navController::popBackStack,
                    onPick = { packageName ->
                        // El selector no se queda en la pila: al volver del editor se llega a la lista, no a la búsqueda.
                        navController.navigate(LimitEditorRoute(packageName)) {
                            popUpTo<AppPickerRoute> { inclusive = true }
                        }
                    },
                )
            }
            composable<LimitEditorRoute> { LimitEditorScreen(onClose = navController::popBackStack) }
            composable<PermissionsRoute> { entry ->
                if (entry.toRoute<PermissionsRoute>().onboarding) {
                    PermissionsScreen(
                        onDone = {
                            viewModel.markOnboardingDone()
                            navController.navigate(TodayRoute) {
                                popUpTo<PermissionsRoute> { inclusive = true }
                            }
                        },
                    )
                } else {
                    PermissionsScreen(onBack = navController::popBackStack)
                }
            }
        }
    }
}

/**
 * Cambia de pestaña sin apilar pantallas: «Hoy» queda siempre en la base, así que Atrás desde cualquier pestaña
 * vuelve a «Hoy» y desde «Hoy» sale de la app. Cada pestaña conserva su estado al volver.
 */
private fun NavHostController.navigateToTab(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo<TodayRoute> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
