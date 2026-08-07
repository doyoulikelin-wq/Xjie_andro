package com.xjie.app.feature.healthconnect

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xjie.app.core.ui.theme.XjieTheme

/** Local, URL-free rationale used by Health Connect's permission-management surfaces. */
class HealthConnectRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            XjieTheme {
                HealthConnectRationale(onClose = ::finish)
            }
        }
    }
}

@Composable
private fun HealthConnectRationale(onClose: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("Health Connect 数据用途", style = MaterialTheme.typography.headlineSmall)
            Text(
                "小捷只读访问步数、距离、睡眠、心率变异性（HRV）、静息心率和体重，用于在你的账号中展示健康数据与趋势。",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "小捷不会向 Health Connect 写入或修改记录。数据仅在你主动同步后上传到当前登录账号；你可以随时在 Health Connect 中撤销权限。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onClose) {
                Text("知道了")
            }
        }
    }
}
