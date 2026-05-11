package com.xjie.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import com.xjie.app.core.ui.theme.XjieTheme
import com.xjie.app.feature.splash.SplashScreen
import com.xjie.app.navigation.AppNavGraph
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* user response, no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ensureNotificationPermission()
        setContent {
            XjieTheme {
                Box(Modifier.fillMaxSize()) {
                    AppNavGraph()
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

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val perm = Manifest.permission.POST_NOTIFICATIONS
            val granted = ContextCompat.checkSelfPermission(this, perm) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) requestNotificationPermission.launch(perm)
        }
    }
}
