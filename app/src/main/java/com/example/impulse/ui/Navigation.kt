package com.example.impulse.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.impulse.data.UserSettings
import com.example.impulse.ui.components.LocalSnackbar
import com.example.impulse.ui.screens.HabitDetailScreen
import com.example.impulse.ui.screens.HabitEditorScreen
import com.example.impulse.ui.screens.HabitsScreen
import com.example.impulse.ui.screens.InsightsScreen
import com.example.impulse.ui.screens.InventoryScreen
import com.example.impulse.ui.screens.ItemDetailScreen
import com.example.impulse.ui.screens.ItemEditorScreen
import com.example.impulse.ui.screens.OnboardingScreen
import com.example.impulse.ui.screens.SettingsScreen
import com.example.impulse.ui.screens.TaskEditorScreen
import com.example.impulse.ui.screens.TasksScreen
import com.example.impulse.ui.screens.TodayScreen

object Routes {
    const val TODAY = "today"
    const val INVENTORY = "inventory?tab={tab}"
    const val TASKS = "tasks"
    const val HABITS = "habits"
    const val INSIGHTS = "insights"
    const val SETTINGS = "settings"
    const val ITEM = "item/{id}"
    const val ITEM_EDIT = "item_edit?id={id}&name={name}"
    const val TASK_EDIT = "task_edit?id={id}&template={template}"
    const val HABIT_EDIT = "habit_edit?id={id}"
    const val HABIT = "habit/{id}"

    fun inventory(tab: Int = 0) = "inventory?tab=$tab"
    fun item(id: Long) = "item/$id"
    fun itemEdit(id: Long? = null, name: String? = null) = "item_edit?id=${id ?: -1}&name=${name ?: ""}"
    fun taskEdit(id: Long? = null, template: Int = -1) = "task_edit?id=${id ?: -1}&template=$template"
    fun habitEdit(id: Long? = null) = "habit_edit?id=${id ?: -1}"
    fun habit(id: Long) = "habit/$id"
}

private data class TopDest(val route: String, val base: String, val label: String, val icon: ImageVector)

private val topDestinations = listOf(
    TopDest(Routes.TODAY, "today", "Today", Icons.Rounded.Home),
    TopDest(Routes.INVENTORY, "inventory", "Inventory", Icons.Rounded.Inventory2),
    TopDest(Routes.TASKS, "tasks", "Tasks", Icons.Rounded.TaskAlt),
    TopDest(Routes.HABITS, "habits", "Habits", Icons.Rounded.SelfImprovement),
    TopDest(Routes.INSIGHTS, "insights", "Insights", Icons.Rounded.Insights),
)

@Composable
fun LifeLoopRoot(settings: UserSettings?, deepLink: String?, onDeepLinkHandled: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            settings == null -> Unit // DataStore is loading (a few ms) — keep the splash background
            !settings.onboarded -> OnboardingScreen()
            else -> MainShell(deepLink, onDeepLinkHandled)
        }
    }
}

@Composable
private fun MainShell(deepLink: String?, onDeepLinkHandled: () -> Unit) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val entry by nav.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route
    val showBar = topDestinations.any { it.route == currentRoute }

    LaunchedEffect(deepLink) {
        val link = deepLink ?: return@LaunchedEffect
        handleDeepLink(nav, link)
        onDeepLinkHandled()
    }

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                AnimatedVisibility(showBar, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest, tonalElevation = 0.dp) {
                        topDestinations.forEach { d ->
                            val selected = currentRoute == d.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTop(nav, if (d.base == "inventory") Routes.inventory() else d.base) },
                                icon = { Icon(d.icon, contentDescription = d.label) },
                                label = { Text(d.label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    indicatorColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = Routes.TODAY,
                modifier = Modifier.padding(padding).consumeWindowInsets(padding),
                enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 12 } },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(220)) { it / 12 } },
            ) {
                composable(Routes.TODAY) { TodayScreen(nav) }
                composable(Routes.INVENTORY, arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 })) {
                    InventoryScreen(nav, initialTab = it.arguments?.getInt("tab") ?: 0)
                }
                composable(Routes.TASKS) { TasksScreen(nav) }
                composable(Routes.HABITS) { HabitsScreen(nav) }
                composable(Routes.INSIGHTS) { InsightsScreen() }
                composable(Routes.SETTINGS) { SettingsScreen(nav) }
                composable(Routes.ITEM, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    ItemDetailScreen(nav, it.arguments?.getLong("id") ?: 0L)
                }
                composable(
                    Routes.ITEM_EDIT,
                    arguments = listOf(
                        navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                        navArgument("name") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) {
                    val id = it.arguments?.getLong("id") ?: -1L
                    ItemEditorScreen(nav, if (id > 0) id else null, it.arguments?.getString("name").orEmpty())
                }
                composable(
                    Routes.TASK_EDIT,
                    arguments = listOf(
                        navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                        navArgument("template") { type = NavType.IntType; defaultValue = -1 },
                    ),
                ) {
                    val id = it.arguments?.getLong("id") ?: -1L
                    TaskEditorScreen(nav, if (id > 0) id else null, it.arguments?.getInt("template") ?: -1)
                }
                composable(Routes.HABIT_EDIT, arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })) {
                    val id = it.arguments?.getLong("id") ?: -1L
                    HabitEditorScreen(nav, if (id > 0) id else null)
                }
                composable(Routes.HABIT, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    HabitDetailScreen(nav, it.arguments?.getLong("id") ?: 0L)
                }
            }
        }
    }
}


fun navigateTop(nav: NavController, route: String) {
    nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun handleDeepLink(nav: NavHostController, link: String) {
    val path = link.removePrefix("lifeloop://")
    when {
        path == "add_item" -> nav.navigate(Routes.itemEdit())
        path == "add_task" -> nav.navigate(Routes.taskEdit())
        path == "shopping" -> navigateTop(nav, Routes.inventory(1))
        path == "inventory" -> navigateTop(nav, Routes.inventory(0))
        path == "tasks" -> navigateTop(nav, "tasks")
        path == "habits" -> navigateTop(nav, "habits")
        path.startsWith("item/") -> path.removePrefix("item/").toLongOrNull()?.let { nav.navigate(Routes.item(it)) }
        else -> navigateTop(nav, "today")
    }
}
