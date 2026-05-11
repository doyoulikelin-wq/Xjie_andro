package com.xjie.app.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.xjie.app.core.ui.components.OfflineBanner
import com.xjie.app.core.util.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

private data class TabItem(val route: Route, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem(Route.Home, "首页", Icons.Default.Home),
    TabItem(Route.HealthData, "健康数据", Icons.Default.Favorite),
    TabItem(Route.Omics, "多组学", Icons.Default.Hub),
    TabItem(Route.Chat, "助手小捷", Icons.AutoMirrored.Filled.Chat),
)

@Composable
fun MainScaffold(
    vm: MainScaffoldViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val isOnline by vm.isOnline.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 14.dp,
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                ),
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                ) {
                    val backStack by navController.currentBackStackEntryAsState()
                    val currentDest = backStack?.destination
                    tabs.forEach { tab ->
                        val selected = isTabSelected(tab.route, currentDest)
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                val current = currentDest?.route
                                if (current == tab.route.path) return@NavigationBarItem
                                // 先把所有非 tab 的二级页面（如血糖/膳食/健康详情等）弹出，
                                // 避免它们被作为 tab 的保存状态恢复。
                                val startId = navController.graph.findStartDestination().id
                                navController.popBackStack(startId, inclusive = false)
                                if (tab.route != Route.Home) {
                                    navController.navigate(tab.route.path) {
                                        popUpTo(startId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding: PaddingValues ->
        androidx.compose.foundation.layout.Column(Modifier.padding(padding).fillMaxSize()) {
            OfflineBanner(isOnline = isOnline)
            NavHost(
                navController = navController,
                startDestination = Route.Home.path,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(Route.Home.path) {
                    com.xjie.app.feature.home.HomeScreen(
                        onOpenSettings = { navController.navigate(Route.Settings.path) },
                        onOpenGlucose = { navController.navigate(Route.Glucose.path) },
                        onOpenMeals = { navController.navigate(Route.Meals.path) },
                        onOpenChat = {
                            navController.navigate(Route.Chat.path) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onOpenHealth = { navController.navigate(Route.Health.path) },
                    )
                }
                composable(Route.Glucose.path) {
                    com.xjie.app.feature.glucose.GlucoseScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Route.Meals.path) {
                    com.xjie.app.feature.meals.MealsScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Route.Health.path) {
                    com.xjie.app.feature.health.HealthScreen(
                        onBack = { navController.popBackStack() },
                        onOpenMood = { navController.navigate(Route.Mood.path) },
                    )
                }
                composable(Route.HealthData.path) {
                    com.xjie.app.feature.healthdata.HealthDataScreen(
                        onOpenRecords = { navController.navigate(Route.MedicalRecords.path) },
                        onOpenExams = { navController.navigate(Route.ExamReports.path) },
                    )
                }
                composable(
                    Route.HealthDataFocus.PATTERN,
                    arguments = listOf(androidx.navigation.navArgument("focus") {
                        type = androidx.navigation.NavType.StringType
                    }),
                ) { entry ->
                    val focus = entry.arguments?.getString("focus")
                    com.xjie.app.feature.healthdata.HealthDataScreen(
                        onOpenRecords = { navController.navigate(Route.MedicalRecords.path) },
                        onOpenExams = { navController.navigate(Route.ExamReports.path) },
                        initialFocus = focus,
                    )
                }
                composable(Route.Omics.path) {
                    com.xjie.app.feature.omics.OmicsScreen()
                }
                composable(Route.Chat.path) {
                    com.xjie.app.feature.chat.ChatScreen(
                        onOpenPatientHistory = { navController.navigate(Route.PatientHistory.path) },
                    )
                }
                composable(Route.PatientHistory.path) {
                    com.xjie.app.feature.patienthistory.PatientHistoryScreen(
                        onBack = { navController.popBackStack() },
                        onOpenHealthDataFocus = { focus ->
                            navController.navigate(Route.HealthDataFocus(focus).path)
                        },
                    )
                }
                composable(Route.MedicalRecords.path) {
                    com.xjie.app.feature.healthdata.DocumentListScreen(
                        docType = "record",
                        title = "历史病例",
                        onBack = { navController.popBackStack() },
                        onItemClick = { id -> navController.navigate("document/$id") },
                    )
                }
                composable(Route.ExamReports.path) {
                    com.xjie.app.feature.healthdata.DocumentListScreen(
                        docType = "exam",
                        title = "历史体检",
                        onBack = { navController.popBackStack() },
                        onItemClick = { id -> navController.navigate("document/$id") },
                    )
                }
                composable(Route.Settings.path) {
                    com.xjie.app.feature.settings.SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenAdmin = { navController.navigate(Route.Admin.path) },
                    )
                }
                composable(Route.Admin.path) {
                    com.xjie.app.feature.admin.AdminScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Route.Mood.path) {
                    com.xjie.app.feature.mood.MoodScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    Route.DocumentDetail.PATTERN,
                    arguments = listOf(androidx.navigation.navArgument("id") {
                        type = androidx.navigation.NavType.StringType
                    }),
                ) { entry ->
                    val id = entry.arguments?.getString("id").orEmpty()
                    com.xjie.app.feature.healthdata.DocumentDetailScreen(
                        docId = id,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

private fun isTabSelected(tabRoute: Route, currentDest: NavDestination?): Boolean {
    return when (tabRoute) {
        Route.HealthData -> currentDest?.hierarchy?.any {
            it.route == Route.HealthData.path || it.route == Route.HealthDataFocus.PATTERN
        } == true

        Route.Chat -> currentDest?.hierarchy?.any {
            it.route == Route.Chat.path || it.route == Route.PatientHistory.path
        } == true

        else -> currentDest?.hierarchy?.any { it.route == tabRoute.path } == true
    }
}

@Composable
private fun TabPlaceholder(label: String) {
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) { Text(label) }
}

@HiltViewModel
class MainScaffoldViewModel @Inject constructor(monitor: NetworkMonitor) : ViewModel() {
    val isOnline: StateFlow<Boolean> = monitor.isOnline
}
