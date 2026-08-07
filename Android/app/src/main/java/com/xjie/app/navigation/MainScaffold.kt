package com.xjie.app.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Assignment
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
    TabItem(Route.HealthPlan, "计划", Icons.Default.Assignment),
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
    ) { padding: PaddingValues ->
        androidx.compose.foundation.layout.Column(Modifier.padding(padding).fillMaxSize()) {
            OfflineBanner(isOnline = isOnline)
            NavHost(
                navController = navController,
                startDestination = Route.XAgeShell.path,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(Route.XAgeShell.path) {
                    com.xjie.app.feature.xage.XAgeMainScreen(
                        onOpenPanelDestination = { destination ->
                            val path = when (destination) {
                                "meals" -> Route.Meals.path
                                "mood" -> Route.Mood.path
                                "weight" -> Route.Weight.path
                                "reports" -> Route.ExamReports.path
                                "medications" -> Route.Medications.path
                                "health_plan" -> Route.HealthPlan.path
                                "medical" -> Route.MedicalAssistant.path
                                "profile" -> Route.PatientHistory.path
                                "device" -> Route.SettingsFocus("device").path
                                "account" -> Route.SettingsFocus("account").path
                                "family" -> Route.FamilyMode.path
                                "support" -> Route.SettingsFocus("support").path
                                "support_help" -> Route.SettingsFocus("support_help").path
                                "support_version" -> Route.SettingsFocus("support_version").path
                                "support_privacy" -> Route.SettingsFocus("support_privacy").path
                                "support_permissions" -> Route.SettingsFocus("support_permissions").path
                                "support_feedback" -> Route.SettingsFocus("support_feedback").path
                                "daily" -> Route.Health.path
                                else -> null
                            }
                            path?.let { navController.navigate(it) { launchSingleTop = true } }
                        },
                    )
                }
                composable(Route.Home.path) {
                    com.xjie.app.feature.home.HomeScreen(
                        onBack = { navController.popBackStack() },
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
	                        onOpenHealthData = {
	                            navController.navigate(Route.HealthData.path) {
	                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
	                                launchSingleTop = true
	                                restoreState = true
	                            }
	                        },
	                        onOpenElderlyHistory = { navController.navigate(Route.ElderlyHistory.path) },
	                        onOpenOmics = {
	                            navController.navigate(Route.Omics.path) {
	                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
	                                launchSingleTop = true
	                                restoreState = true
	                            }
	                        },
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
                        onOpenRecords = { navController.navigate(Route.MedicalAssistant.path) },
                        onOpenExams = { navController.navigate(Route.ExamReports.path) },
                        onOpenPatientHistory = { navController.navigate(Route.PatientHistory.path) },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Route.Weight.path) {
                    com.xjie.app.feature.weight.WeightScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Route.HealthPlan.path) {
                    com.xjie.app.feature.healthplan.HealthPlanScreen(
                        onBack = { navController.popBackStack() },
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
                        onOpenRecords = { navController.navigate(Route.MedicalAssistant.path) },
                        onOpenExams = { navController.navigate(Route.ExamReports.path) },
                        onOpenPatientHistory = { navController.navigate(Route.PatientHistory.path) },
                        initialFocus = focus,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Route.Omics.path) {
                    com.xjie.app.feature.omics.OmicsScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Route.Chat.path) {
                    com.xjie.app.feature.chat.ChatScreen(
                        onBack = { navController.popBackStack() },
                        onOpenPatientHistory = { navController.navigate(Route.PatientHistory.path) },
                    )
                }
                composable(Route.PatientHistory.path) {
                    com.xjie.app.feature.patienthistory.PatientHistoryScreen(
                        onBack = { navController.popBackStack() },
                        onOpenHealthDataFocus = { focus ->
                            navController.navigate(Route.HealthDataFocus(focus).path)
                        },
                        onOpenMedications = { navController.navigate(Route.Medications.path) },
                        onOpenHealthPlan = { navController.navigate(Route.HealthPlan.path) },
                    )
                }
                composable(Route.MedicalRecords.path) {
                    com.xjie.app.feature.healthdata.DocumentListScreen(
                        docType = "record",
                        title = "就医助手",
                        onBack = { navController.popBackStack() },
                        onItemClick = { id -> navController.navigate("document/$id") },
                    )
                }
                composable(Route.MedicalAssistant.path) {
                    com.xjie.app.feature.medicalassistant.MedicalAssistantScreen(
                        onClose = { navController.popBackStack() },
                        onOpenDocument = { id ->
                            navController.navigate(Route.DocumentDetail(id).path)
                        },
                    )
                }
                composable(Route.ExamReports.path) {
                    com.xjie.app.feature.healthdata.DocumentListScreen(
                        docType = "exam",
                        title = "健康报告",
                        onBack = { navController.popBackStack() },
                        onItemClick = { id -> navController.navigate("document/$id") },
                    )
                }
                composable(Route.Settings.path) {
                    com.xjie.app.feature.settings.SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenAdmin = { navController.navigate(Route.Admin.path) },
                        onOpenElderlyHistory = { navController.navigate(Route.ElderlyHistory.path) },
                        onOpenFamily = { navController.navigate(Route.FamilyMode.path) },
                        onOpenMedications = { navController.navigate(Route.Medications.path) },
                    )
                }
                composable(
                    Route.SettingsFocus.PATTERN,
                    arguments = listOf(androidx.navigation.navArgument("focus") {
                        type = androidx.navigation.NavType.StringType
                    }),
                ) { entry ->
                    com.xjie.app.feature.settings.SettingsScreen(
                        initialSection = entry.arguments?.getString("focus"),
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Route.FamilyMode.path) {
                    com.xjie.app.feature.family.FamilyModeScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Route.Medications.path) {
                    com.xjie.app.feature.medication.MedicationListScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Route.ElderlyHistory.path) {
                    com.xjie.app.feature.elderly.ElderlyHistoryScreen(
                        onBack = { navController.popBackStack() },
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
