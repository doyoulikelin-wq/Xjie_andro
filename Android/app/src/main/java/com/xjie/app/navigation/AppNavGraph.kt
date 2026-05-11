package com.xjie.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.xjie.app.core.auth.AuthManager
import com.xjie.app.feature.login.LoginScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * 顶层导航 —— 登录态变化自动切换；已登录进入 [MainScaffold]。
 */
@Composable
fun AppNavGraph(vm: AppNavViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val navController = rememberNavController()

    val target = if (state.isLoggedIn) Route.Main.path else Route.Login.path

    LaunchedEffect(state.isLoggedIn) {
        val current = navController.currentDestination?.route
        if (current != null && current != target) {
            navController.navigate(target) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(navController = navController, startDestination = target) {
        composable(Route.Splash.path) { Placeholder("Splash") }
        composable(Route.Login.path) { LoginScreen() }
        composable(Route.Main.path) { MainScaffold() }
    }
}

@Composable
private fun Placeholder(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(label) }
}

@HiltViewModel
class AppNavViewModel @Inject constructor(auth: AuthManager) : ViewModel() {
    val state: StateFlow<AuthManager.State> = auth.state
}
