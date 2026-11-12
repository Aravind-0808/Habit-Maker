package com.aravind.habitmaker.navigation

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.aravind.habitmaker.app
import com.aravind.habitmaker.feature.home.HomeState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aravind.habitmaker.feature.blocking.AppLimitsScreen
import com.aravind.habitmaker.feature.blocking.BlockScreen
import com.aravind.habitmaker.feature.blocking.DistractingAppsScreen
import com.aravind.habitmaker.feature.blocking.ScheduleEditScreen
import com.aravind.habitmaker.feature.blocking.ScheduleScreen
import com.aravind.habitmaker.feature.blocking.WebsitesScreen
import com.aravind.habitmaker.feature.focus.FocusScreen
import com.aravind.habitmaker.feature.habits.HabitDetailScreen
import com.aravind.habitmaker.feature.habits.HabitEditScreen
import com.aravind.habitmaker.feature.habits.HabitsScreen
import com.aravind.habitmaker.feature.home.HomeScreen
import com.aravind.habitmaker.feature.permissions.PermissionsScreen
import com.aravind.habitmaker.feature.prep.AlarmSettingsScreen
import com.aravind.habitmaker.feature.prep.BlockEditScreen
import com.aravind.habitmaker.feature.prep.PrepCalendarScreen
import com.aravind.habitmaker.feature.prep.PrepScreen
import com.aravind.habitmaker.feature.prep.PrepSetupScreen
import com.aravind.habitmaker.feature.prep.RoutineEditScreen
import com.aravind.habitmaker.feature.profile.InfoScreen
import com.aravind.habitmaker.feature.profile.PremiumScreen
import com.aravind.habitmaker.feature.profile.ProfileScreen
import com.aravind.habitmaker.feature.stats.AppUsageScreen
import com.aravind.habitmaker.feature.stats.StatsScreen
import com.aravind.habitmaker.ui.theme.colors

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.HOME, "Home", Icons.Filled.Home),
    Tab(Routes.PREP, "Prep", Icons.Filled.School),
    Tab(Routes.FOCUS, "Focus", Icons.Filled.Timer),
    Tab(Routes.BLOCK, "Block", Icons.Filled.Block),
    Tab(Routes.STATS, "Stats", Icons.Filled.BarChart),
)

private fun NavHostController.tab(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Bottom tabs and every page of the app. [openRoute] jumps to a tab (from a notification). */
@Composable
fun AppShell(openRoute: String? = null) {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()
    val homeState = remember(app, scope) { HomeState(app, scope) }
    // Start directly at a notification's tab instead of composing Home first.
    val startRoute = remember { openRoute?.takeIf { it in Routes.TABS } ?: Routes.HOME }
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: startRoute
    val c = MaterialTheme.colors
    val back: () -> Unit = { nav.popBackStack() }
    val go: (String) -> Unit = { r -> if (r in Routes.TABS) nav.tab(r) else nav.navigate(r) }

    LaunchedEffect(openRoute) { if (openRoute != null && openRoute in Routes.TABS) nav.tab(openRoute) }

    Scaffold(
        containerColor = c.background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (route in Routes.TABS) {
                NavigationBar(containerColor = c.surface, tonalElevation = 2.dp) {
                    TABS.forEach { t ->
                        val selected = route == t.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { nav.tab(t.route) },
                            icon = { Icon(t.icon, null) },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = c.primary,
                                selectedTextColor = c.primary,
                                indicatorColor = c.primarySoft,
                                unselectedIconColor = c.faint,
                                unselectedTextColor = c.faint,
                            ),
                        )
                    }
                }
            }
        },
    ) { pad ->
        NavHost(
            navController = nav,
            startDestination = startRoute,
            modifier = Modifier.fillMaxSize().padding(pad),
            enterTransition = {
                if (initialState.destination.route in Routes.TABS && targetState.destination.route in Routes.TABS) {
                    fadeIn(tween(180))
                } else slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { it / 12 } + fadeIn(tween(180))
            },
            exitTransition = {
                if (initialState.destination.route in Routes.TABS && targetState.destination.route in Routes.TABS) {
                    fadeOut(tween(140))
                } else slideOutHorizontally(tween(240, easing = FastOutSlowInEasing)) { -it / 24 } + fadeOut(tween(140))
            },
            popEnterTransition = {
                if (initialState.destination.route in Routes.TABS && targetState.destination.route in Routes.TABS) {
                    fadeIn(tween(180))
                } else slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { -it / 24 } + fadeIn(tween(180))
            },
            popExitTransition = {
                if (initialState.destination.route in Routes.TABS && targetState.destination.route in Routes.TABS) {
                    fadeOut(tween(140))
                } else slideOutHorizontally(tween(240, easing = FastOutSlowInEasing)) { it / 12 } + fadeOut(tween(140))
            },
        ) {
            composable(Routes.HOME) { HomeScreen(go, homeState) }
            composable(Routes.PREP) { PrepScreen(go) }
            composable(Routes.HABITS) { HabitsScreen(go, onBack = back) }
            composable(Routes.PREP_SETUP) {
                // Go back to the Prep tab that is already under the setup page. (nav.tab() would restore
                // the tab's saved history — with this setup page still on top.)
                PrepSetupScreen(back) { if (!nav.popBackStack(Routes.PREP, inclusive = false)) nav.tab(Routes.PREP) }
            }
            composable(Routes.PREP_CALENDAR) { PrepCalendarScreen(back) }
            composable(Routes.ROUTINE_EDIT) { RoutineEditScreen(back, go) }
            composable(Routes.ROUTINE_BLOCK, listOf(navArgument("id") { type = NavType.LongType })) {
                BlockEditScreen(it.arguments?.getLong("id") ?: 0L, back)
            }
            composable(Routes.ALARM_SETTINGS) { AlarmSettingsScreen(back, go) }
            composable(Routes.FOCUS) { FocusScreen() }
            composable(Routes.BLOCK) { BlockScreen(go) }
            composable(Routes.STATS) { StatsScreen(go) }

            composable(Routes.PROFILE) { ProfileScreen(back, go) }
            composable(Routes.PERMISSIONS) { PermissionsScreen(onBack = back) }
            composable(Routes.PREMIUM) { PremiumScreen(back) }
            composable(Routes.INFO, listOf(navArgument("kind") { type = NavType.StringType })) {
                InfoScreen(it.arguments?.getString("kind").orEmpty(), back)
            }
            composable(Routes.HABIT_EDIT, listOf(navArgument("id") { type = NavType.LongType })) {
                HabitEditScreen(it.arguments?.getLong("id") ?: 0L, back, back)
            }
            composable(Routes.HABIT_DETAIL, listOf(navArgument("id") { type = NavType.LongType })) {
                HabitDetailScreen(it.arguments?.getLong("id") ?: 0L, back, go)
            }
            composable(Routes.BLOCK_APPS) { DistractingAppsScreen(back) }
            composable(Routes.BLOCK_LIMITS) { AppLimitsScreen(back) }
            composable(Routes.BLOCK_SCHEDULE) { ScheduleScreen(back, go) }
            composable(Routes.SCHEDULE_EDIT, listOf(navArgument("id") { type = NavType.LongType })) {
                ScheduleEditScreen(it.arguments?.getLong("id") ?: 0L, back)
            }
            composable(Routes.BLOCK_SITES) { WebsitesScreen(back) }
            composable(Routes.APP_USAGE, listOf(navArgument("pkg") { type = NavType.StringType })) {
                AppUsageScreen(it.arguments?.getString("pkg").orEmpty(), back)
            }
        }
    }
}
