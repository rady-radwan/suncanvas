package app.suncanvas.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.suncanvas.ui.screens.calendar.CalendarScreen
import app.suncanvas.ui.screens.cities.CitiesScreen
import app.suncanvas.ui.screens.cities.CitySearchScreen
import app.suncanvas.ui.screens.cities.CoordinatesScreen
import app.suncanvas.ui.screens.cities.LocationDetailScreen
import app.suncanvas.ui.screens.home.HomeScreen
import app.suncanvas.ui.screens.onboarding.OnboardingScreen
import app.suncanvas.ui.screens.settings.AboutScreen
import app.suncanvas.ui.screens.settings.RemindersScreen
import app.suncanvas.ui.screens.settings.SettingsScreen
import app.suncanvas.ui.screens.widgets.WidgetEditorScreen
import app.suncanvas.ui.screens.widgets.WidgetsScreen

object Routes {
    const val HOME = "home"
    const val CITIES = "cities"
    const val CALENDAR = "calendar"
    const val WIDGETS = "widgets"
    const val SETTINGS = "settings"
    const val ONBOARDING = "onboarding"
    const val SEARCH = "search?mode={mode}"
    const val COORDINATES = "coordinates"
    const val LOCATION = "location/{id}"
    const val EDITOR = "editor?designId={designId}&appWidgetId={appWidgetId}&template={template}"
    const val REMINDERS = "reminders"
    const val ABOUT = "about"

    fun search(mode: String) = "search?mode=$mode"
    fun location(id: Long) = "location/$id"
    fun editor(designId: Long? = null, appWidgetId: Int? = null, template: String? = null) =
        "editor?designId=${designId ?: -1}&appWidgetId=${appWidgetId ?: -1}&template=${template ?: ""}"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Rounded.WbSunny),
    Tab(Routes.CITIES, "Cities", Icons.Rounded.Public),
    Tab(Routes.CALENDAR, "Calendar", Icons.Rounded.CalendarMonth),
    Tab(Routes.WIDGETS, "Widgets", Icons.Rounded.Widgets),
    Tab(Routes.SETTINGS, "Settings", Icons.Rounded.Settings),
)

@Composable
fun SunCanvasRoot(vm: AppViewModel) {
    val nav = rememberNavController()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val message by vm.messages.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }

    val loaded = settings ?: return
    val start = if (loaded.onboardingDone) Routes.HOME else Routes.ONBOARDING

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { nav.navigateTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = start,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(vm, onDone = {
                    vm.finishOnboarding()
                    nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                }, onSearch = { nav.navigate(Routes.search("onboarding")) }, onCoordinates = { nav.navigate(Routes.COORDINATES) })
            }
            composable(Routes.HOME) {
                HomeScreen(vm, onOpenLocation = { nav.navigate(Routes.location(it)) }, onPickLocation = { nav.navigate(Routes.search("select")) })
            }
            composable(Routes.CITIES) {
                CitiesScreen(vm, onAdd = { nav.navigate(Routes.search("add")) }, onOpen = { nav.navigate(Routes.location(it)) }, onCoordinates = { nav.navigate(Routes.COORDINATES) })
            }
            composable(Routes.CALENDAR) { CalendarScreen(vm) }
            composable(Routes.WIDGETS) {
                WidgetsScreen(
                    onNewFromTemplate = { nav.navigate(Routes.editor(template = it)) },
                    onEditDesign = { nav.navigate(Routes.editor(designId = it)) },
                    onEditWidget = { nav.navigate(Routes.editor(appWidgetId = it)) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(vm, onReminders = { nav.navigate(Routes.REMINDERS) }, onAbout = { nav.navigate(Routes.ABOUT) },
                    onPickDefault = { nav.navigate(Routes.CITIES) })
            }
            composable(Routes.SEARCH, arguments = listOf(navArgument("mode") { type = NavType.StringType; defaultValue = "add" })) { entry ->
                val mode = entry.arguments?.getString("mode") ?: "add"
                CitySearchScreen(vm, mode = mode, onBack = { nav.popBackStack() }, onDone = {
                    if (mode == "onboarding") {
                        vm.finishOnboarding()
                        nav.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } }
                    } else {
                        nav.popBackStack()
                    }
                }, onCoordinates = { nav.navigate(Routes.COORDINATES) })
            }
            composable(Routes.COORDINATES) {
                CoordinatesScreen(vm, onBack = { nav.popBackStack() }, onDone = {
                    if (!loaded.onboardingDone) {
                        vm.finishOnboarding()
                        nav.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } }
                    } else {
                        nav.popBackStack()
                    }
                })
            }
            composable(Routes.LOCATION, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                LocationDetailScreen(vm, id = entry.arguments?.getLong("id") ?: -1L, onBack = { nav.popBackStack() })
            }
            composable(
                Routes.EDITOR,
                arguments = listOf(
                    navArgument("designId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("appWidgetId") { type = NavType.IntType; defaultValue = -1 },
                    navArgument("template") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                WidgetEditorScreen(
                    designId = entry.arguments?.getLong("designId")?.takeIf { it > 0 },
                    appWidgetId = entry.arguments?.getInt("appWidgetId")?.takeIf { it > 0 },
                    templateId = entry.arguments?.getString("template")?.takeIf { it.isNotBlank() },
                    onClose = { nav.popBackStack() },
                )
            }
            composable(Routes.REMINDERS) { RemindersScreen(vm, onBack = { nav.popBackStack() }) }
            composable(Routes.ABOUT) { AboutScreen(onBack = { nav.popBackStack() }) }
        }
    }
}

private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
