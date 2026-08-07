package com.xjie.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.xjie.app.core.ui.theme.XjieTheme
import com.xjie.app.core.update.AppUpdatePrompt
import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.quality.UiAutomationRuntime
import com.xjie.app.feature.splash.SplashScreen
import com.xjie.app.navigation.AppNavGraph
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiAutomationRuntime.activateIfExplicit(intent.getStringExtra(UiAutomationRuntime.INTENT_EXTRA))
        UiAutomationRuntime.bootstrapAuth(
            authManager,
            authenticated = intent.getBooleanExtra(
                UiAutomationRuntime.INTENT_AUTHENTICATED_EXTRA,
                true,
            ),
        )
        enableEdgeToEdge()
        setContent {
            XjieTheme {
                Box(Modifier.fillMaxSize()) {
                    AppNavGraph()
                    if (!BuildConfig.DEBUG) {
                        AppUpdatePrompt()
                        var splashVisible by remember { mutableStateOf(true) }
                        AnimatedVisibility(
                            visible = splashVisible,
                            enter = androidx.compose.animation.EnterTransition.None,
                            exit = fadeOut(tween(420)) + scaleOut(
                                targetScale = 1.08f,
                                animationSpec = tween(420),
                            ),
                        ) {
                            SplashScreen(onFinished = { splashVisible = false })
                        }
                    }
                }
            }
        }
    }

}
