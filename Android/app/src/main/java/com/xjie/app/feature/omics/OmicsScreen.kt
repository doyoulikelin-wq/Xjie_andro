package com.xjie.app.feature.omics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xjie.app.core.model.GenomicsDemoPanel
import com.xjie.app.core.model.MetabolomicsDemoPanel
import com.xjie.app.core.model.MicrobiomeDemoPanel
import com.xjie.app.core.model.MicrobiomeTaxon
import com.xjie.app.core.model.OmicsDemoItem
import com.xjie.app.core.model.OmicsTriadInsight
import com.xjie.app.core.model.ProteomicsDemoPanel
import com.xjie.app.core.ui.components.DemoBadge
import com.xjie.app.core.ui.components.EmptyState
import com.xjie.app.core.ui.components.LoadingIndicator
import com.xjie.app.core.ui.components.MarkdownText
import com.xjie.app.core.ui.theme.XjiePalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OmicsScreen(
    onBack: (() -> Unit)? = null,
    vm: OmicsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.fetchAll() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { com.xjie.app.core.ui.components.BrandTitle("多组学") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OmicsTabPills(current = state.tab, onSelect = vm::setTab)

            if (state.loading) {
                Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            } else {
                AnimatedContent(
                    targetState = state.tab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "omics_tab",
                ) { tab ->
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        when (tab) {
                            OmicsTab.Metabolomics -> {
                                state.metabolomics?.let { MetabPanel(it) } ?: EmptyState("暂无代谢组数据")
                                state.triad?.let { TriadPanel(it) }
                            }
                            OmicsTab.Proteomics -> state.proteomics?.let { ProtPanel(it) }
                                ?: EmptyState("暂无蛋白组数据")
                            OmicsTab.Genomics -> {
                                state.genomics?.let { GenPanel(it) } ?: EmptyState("暂无基因组数据")
                                state.microbiome?.let { MicroPanel(it) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ---------- Tab pills ---------- */

@Composable
private fun OmicsTabPills(current: OmicsTab, onSelect: (OmicsTab) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            OmicsTab.entries.forEach { t ->
                val selected = t == current
                val bg = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent
                val fg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .let { if (selected) it.shadow(2.dp, RoundedCornerShape(10.dp)) else it },
                    color = bg,
                    onClick = { onSelect(t) },
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            t.label,
                            color = fg,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

/* ---------- Hero gradient card ---------- */

private data class HeroMetric(val label: String, val value: String, val unit: String? = null)

@Composable
private fun HeroCard(
    title: String,
    subtitle: String?,
    accent: Color,
    isDemo: Boolean,
    metrics: List<HeroMetric>,
    summary: String? = null,
) {
    val gradient = Brush.linearGradient(
        colors = listOf(accent.copy(alpha = 0.95f), accent.copy(alpha = 0.65f)),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(gradient)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (isDemo) DemoBadge()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            metrics.forEach { m -> HeroMetricView(m, modifier = Modifier.weight(1f)) }
        }
        if (!summary.isNullOrBlank()) {
            Surface(color = Color.White.copy(alpha = 0.15f), shape = RoundedCornerShape(10.dp)) {
                Box(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    CompositionLocalProvider(LocalContentColor provides Color.White) {
                        MarkdownText(summary)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroMetricView(m: HeroMetric, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Text(
            m.label,
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                m.value,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
            )
            if (!m.unit.isNullOrBlank()) {
                Spacer(Modifier.width(4.dp))
                Text(m.unit, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
            }
        }
    }
}

/* ---------- Section header ---------- */

@Composable
private fun SectionHeader(title: String, subtitle: String? = null, accent: Color = XjiePalette.Primary) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp),
    ) {
        Box(
            Modifier
                .size(width = 4.dp, height = 18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/* ---------- Panels ---------- */

@Composable
private fun MetabPanel(p: MetabolomicsDemoPanel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HeroCard(
            title = "代谢组总览",
            subtitle = "基于血液代谢组学指标的综合评估",
            accent = XjiePalette.Primary,
            isDemo = p.is_demo,
            metrics = listOf(
                HeroMetric("代谢年龄Δ", "%+.1f".format(p.metabolic_age_delta_years), "岁"),
                HeroMetric("总体风险", riskLabelZh(p.overall_risk)),
            ),
            summary = p.summary,
        )
        SectionHeader("代谢标志物", "${p.items.size} 项关键代谢物", XjiePalette.Primary)
        p.items.forEach { OmicsItemRow(it) }
    }
}

@Composable
private fun ProtPanel(p: ProteomicsDemoPanel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HeroCard(
            title = "蛋白组总览",
            subtitle = "炎症与免疫相关蛋白评估",
            accent = XjiePalette.Warning,
            isDemo = p.is_demo,
            metrics = listOf(
                HeroMetric("炎症评分", "%.2f".format(p.inflammation_score)),
            ),
            summary = p.summary,
        )
        SectionHeader("蛋白标志物", "${p.items.size} 项关键蛋白", XjiePalette.Warning)
        p.items.forEach { OmicsItemRow(it) }
    }
}

@Composable
private fun GenPanel(p: GenomicsDemoPanel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            XjiePalette.Primary.copy(alpha = 0.95f),
                            XjiePalette.Accent.copy(alpha = 0.85f),
                        ),
                    ),
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "基因风险评分 PRS",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "0.0 ~ 1.0,越高代表多基因累积风险越大",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (p.is_demo) DemoBadge()
            }
            PrsBar("2 型糖尿病 T2D", p.prs.t2d)
            PrsBar("心血管疾病 CVD", p.prs.cvd)
            PrsBar("代谢相关脂肪肝 MASLD", p.prs.masld)
            if (p.summary.isNotBlank()) {
                Surface(color = Color.White.copy(alpha = 0.15f), shape = RoundedCornerShape(10.dp)) {
                    Box(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        CompositionLocalProvider(LocalContentColor provides Color.White) {
                            MarkdownText(p.summary)
                        }
                    }
                }
            }
        }
        SectionHeader("关键基因变异", "${p.variants.size} 个相关位点", XjiePalette.Primary)
        p.variants.forEach { v ->
            EvidenceCard(
                accent = riskColor(v.risk_level),
                title = v.name,
                statusText = riskLabelZh(v.risk_level),
                statusColor = riskColor(v.risk_level),
                trailing = v.genotype,
                story = v.story_zh,
                chips = v.relevance,
            )
        }
    }
}

@Composable
private fun PrsBar(label: String, value: Double) {
    val v = value.toFloat().coerceIn(0f, 1f)
    val color = when {
        v < 0.34f -> XjiePalette.Success
        v < 0.67f -> XjiePalette.Warning
        else -> XjiePalette.Danger
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(
                label,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                "%.2f".format(value),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        LinearProgressIndicator(
            progress = { v },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = Color.White.copy(alpha = 0.25f),
            strokeCap = StrokeCap.Round,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun MicroPanel(p: MicrobiomeDemoPanel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HeroCard(
            title = "肠道菌群总览",
            subtitle = "多样性与功能菌群结构",
            accent = XjiePalette.Success,
            isDemo = p.is_demo,
            metrics = listOf(
                HeroMetric("Shannon", "%.2f".format(p.shannon)),
                HeroMetric("SCFA 产生菌", "%.0f".format(p.scfa_producer_pct), "%"),
            ),
            summary = p.summary,
        )
        SectionHeader("代表菌属", "${p.taxa.size} 个关键菌属", XjiePalette.Success)
        p.taxa.forEach { TaxonRow(it) }
    }
}

@Composable
private fun TriadPanel(t: OmicsTriadInsight) {
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 4.dp, height = 18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(XjiePalette.Accent),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "多组学关联洞察",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (t.is_demo) DemoBadge()
        }
        ScoreBar("代谢", t.metabolomics_score, XjiePalette.Primary)
        ScoreBar("CGM", t.cgm_score, XjiePalette.Accent)
        ScoreBar("心率", t.heart_score, XjiePalette.Warning)
        ScoreBar("重叠", t.overlap_score, XjiePalette.Success)
        if (t.insights.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            t.insights.forEach { ins ->
                Row {
                    Box(
                        Modifier
                            .padding(top = 7.dp, end = 8.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(XjiePalette.Accent),
                    )
                    Text(
                        ins,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreBar(label: String, value: Double, accent: Color) {
    val v = value.toFloat().coerceIn(0f, 1f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.width(56.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { v },
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = accent,
            trackColor = accent.copy(alpha = 0.15f),
            strokeCap = StrokeCap.Round,
            drawStopIndicator = {},
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "%.2f".format(value),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/* ---------- Item rows ---------- */

@Composable
private fun OmicsItemRow(item: OmicsDemoItem) {
    val accent = statusColor(item.status)
    EvidenceCard(
        accent = accent,
        title = item.name,
        statusText = statusLabelZh(item.status),
        statusColor = accent,
        trailing = "%.2f %s".format(item.value, item.unit),
        reference = "参考: ${item.reference}",
        story = item.story_zh,
        chips = item.relevance,
    )
}

@Composable
private fun TaxonRow(t: MicrobiomeTaxon) {
    val accent = statusColor(t.status)
    val pct = (t.relative_abundance.toFloat().coerceIn(0f, 100f)) / 100f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 3.dp, height = 16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                t.name,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            StatusPill(statusLabelZh(t.status), accent)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = { pct },
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = accent,
                trackColor = accent.copy(alpha = 0.15f),
                strokeCap = StrokeCap.Round,
                drawStopIndicator = {},
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "%.2f%%".format(t.relative_abundance),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            "参考区间: ${t.reference}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (t.story_zh.isNotBlank()) {
            Text(
                t.story_zh,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (t.relevance.isNotEmpty()) RelevanceChips(t.relevance, accent)
    }
}

@Composable
private fun EvidenceCard(
    accent: Color,
    title: String,
    statusText: String,
    statusColor: Color,
    trailing: String,
    story: String,
    chips: List<String>,
    reference: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 3.dp, height = 16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            StatusPill(statusText, statusColor)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                trailing,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!reference.isNullOrBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    reference,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (story.isNotBlank()) {
            Text(
                story,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (chips.isNotEmpty()) RelevanceChips(chips, accent)
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        contentColor = color,
        shape = RoundedCornerShape(20.dp),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RelevanceChips(items: List<String>, accent: Color) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { tag ->
            Surface(
                color = accent.copy(alpha = 0.08f),
                contentColor = accent,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, accent.copy(alpha = 0.4f)),
            ) {
                Text(
                    "#$tag",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/* ---------- helpers ---------- */

private fun statusColor(s: String): Color = when (s.lowercase()) {
    "normal", "优", "良好", "正常" -> XjiePalette.Success
    "watch", "中", "关注", "偏高", "偏低" -> XjiePalette.Warning
    "high", "异常", "高", "低" -> XjiePalette.Danger
    else -> XjiePalette.Primary
}

private fun statusLabelZh(s: String): String = when (s.lowercase()) {
    "normal" -> "正常"
    "watch" -> "关注"
    "high" -> "偏高"
    "low" -> "偏低"
    else -> s
}

private fun riskColor(s: String): Color = when (s.lowercase()) {
    "low", "低" -> XjiePalette.Success
    "moderate", "medium", "中" -> XjiePalette.Warning
    "high", "高" -> XjiePalette.Danger
    else -> XjiePalette.Primary
}

private fun riskLabelZh(s: String): String = when (s.lowercase()) {
    "low" -> "低"
    "moderate", "medium" -> "中"
    "high" -> "高"
    else -> s
}
