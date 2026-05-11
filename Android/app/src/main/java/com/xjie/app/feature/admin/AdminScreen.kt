package com.xjie.app.feature.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xjie.app.core.model.AdminFeatureFlag
import com.xjie.app.core.network.api.AdminConversationItem
import com.xjie.app.core.network.api.AdminOmicsItem
import com.xjie.app.core.network.api.AdminStats
import com.xjie.app.core.network.api.AdminTokenStats
import com.xjie.app.core.network.api.AdminUserItem
import com.xjie.app.core.ui.components.EmptyState
import com.xjie.app.core.ui.components.LoadingIndicator
import com.xjie.app.core.ui.components.MetricItem
import com.xjie.app.core.ui.theme.XjiePalette
import com.xjie.app.core.ui.theme.cardStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    onBack: () -> Unit,
    vm: AdminViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.loadOverview() }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("管理后台") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
            )
        },
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {
            ScrollableTabRow(
                selectedTabIndex = state.tab.ordinal,
                edgePadding = 0.dp,
            ) {
                AdminTab.entries.forEach { t ->
                    Tab(
                        selected = state.tab == t,
                        onClick = { vm.setTab(t) },
                        text = { Text(t.label) },
                    )
                }
            }
            when (state.tab) {
                AdminTab.Overview -> OverviewPanel(state.loading, state.stats)
                AdminTab.Users -> UsersPanel(state.users)
                AdminTab.Conversations -> ConversationsPanel(state.conversations)
                AdminTab.Omics -> OmicsPanel(state.omics)
                AdminTab.Tokens -> TokensPanel(state.tokenStats)
                AdminTab.FeatureFlags -> FlagsPanel(state.featureFlags, vm::toggleFlag)
            }
        }
    }
}

@Composable
private fun OverviewPanel(loading: Boolean, stats: AdminStats?) {
    if (loading) { LoadingIndicator(); return }
    if (stats == null) { EmptyState("暂无数据"); return }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FlowMetricsCard(
            listOf(
                "总用户" to stats.total_users.toString(),
                "近 7 日活跃" to stats.active_users_7d.toString(),
                "总会话" to stats.total_conversations.toString(),
                "总消息" to stats.total_messages.toString(),
                "组学上传" to stats.total_omics_uploads.toString(),
                "餐食记录" to stats.total_meals.toString(),
            )
        )
    }
}

@Composable
private fun FlowMetricsCard(items: List<Pair<String, String>>) {
    Column(Modifier.cardStyle(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("总览", style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold)
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, value) ->
                    Box(Modifier.weight(1f)) {
                        MetricItem(label = label, value = value,
                            accent = XjiePalette.Primary)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun UsersPanel(items: List<AdminUserItem>) {
    if (items.isEmpty()) { EmptyState("暂无用户"); return }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.id }) { u ->
            Column(Modifier.cardStyle(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(u.username ?: u.phone, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium)
                    if (u.is_admin) {
                        Spacer(Modifier.width(6.dp))
                        Chip("管理员", XjiePalette.Primary)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("#${u.id}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(u.phone, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("会话 ${u.conversation_count} · 消息 ${u.message_count}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                u.last_active?.let {
                    Text("最近活跃: ${it.take(10)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ConversationsPanel(items: List<AdminConversationItem>) {
    if (items.isEmpty()) { EmptyState("暂无会话"); return }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.id }) { c ->
            Column(Modifier.cardStyle(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(c.title ?: "未命名对话", fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text("用户 ${c.username ?: c.user_id} · ${c.message_count} 条消息",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                c.updated_at?.let {
                    Text("更新于 ${it.take(16)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun OmicsPanel(items: List<AdminOmicsItem>) {
    if (items.isEmpty()) { EmptyState("暂无组学上传"); return }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.id }) { o ->
            Column(Modifier.cardStyle(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(o.omics_type, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium)
                    o.risk_level?.let {
                        Spacer(Modifier.width(6.dp))
                        Chip(it, riskColor(it))
                    }
                    Spacer(Modifier.weight(1f))
                    Text("用户 ${o.username ?: o.user_id}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                o.file_name?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                o.llm_summary?.let {
                    Text(it, maxLines = 3,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun TokensPanel(stats: AdminTokenStats?) {
    if (stats == null) { LoadingIndicator(); return }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FlowMetricsCard(
            listOf(
                "Prompt" to stats.total_prompt_tokens.toString(),
                "Completion" to stats.total_completion_tokens.toString(),
                "总 Tokens" to stats.total_tokens.toString(),
                "调用次数" to stats.total_calls.toString(),
                "总结任务 Tokens" to stats.summary_task_tokens.toString(),
                "总结任务次数" to stats.summary_task_count.toString(),
            )
        )
        if (stats.by_feature.isNotEmpty()) {
            Column(Modifier.cardStyle(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("按功能分布", fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall)
                stats.by_feature.entries.toList().forEach { (k, v) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(k, modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium)
                        Text("${v.total_tokens} t / ${v.call_count} 次",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun FlagsPanel(
    flags: List<AdminFeatureFlag>,
    onToggle: (AdminFeatureFlag) -> Unit,
) {
    if (flags.isEmpty()) { EmptyState("暂无特性开关"); return }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(flags, key = { it.id }) { f ->
            Column(Modifier.cardStyle(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(f.key, fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium)
                        if (f.description.isNotBlank()) {
                            Text(f.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("rollout ${f.rollout_pct}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = f.enabled,
                        onCheckedChange = { onToggle(f) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp)) {
        Text(text, color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall)
    }
}

private fun riskColor(s: String): Color = when (s.lowercase()) {
    "low", "低" -> XjiePalette.Success
    "moderate", "medium", "中" -> XjiePalette.Warning
    "high", "高" -> XjiePalette.Danger
    else -> XjiePalette.Primary
}
