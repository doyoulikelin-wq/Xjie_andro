package com.xjie.app.feature.medication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.xjie.app.core.ui.theme.XjieTheme
import dagger.hilt.android.AndroidEntryPoint

/** A notification opens the trusted medication destination directly, never an unrelated root. */
@AndroidEntryPoint
class MedicationNotificationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            XjieTheme {
                MedicationListScreen(onBack = ::finish)
            }
        }
    }
}
