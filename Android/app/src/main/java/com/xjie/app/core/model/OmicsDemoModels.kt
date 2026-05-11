package com.xjie.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class OmicsDemoItem(
    val name: String,
    val key: String,
    val value: Double,
    val unit: String,
    val status: String,
    val reference: String,
    val story_zh: String,
    val relevance: List<String>,
)

@Serializable
data class MetabolomicsDemoPanel(
    val is_demo: Boolean,
    val metabolic_age_delta_years: Double,
    val overall_risk: String,
    val summary: String,
    val items: List<OmicsDemoItem>,
)

@Serializable
data class ProteomicsDemoPanel(
    val is_demo: Boolean,
    val inflammation_score: Double,
    val summary: String,
    val items: List<OmicsDemoItem>,
)

@Serializable
data class GeneVariant(
    val name: String,
    val key: String,
    val genotype: String,
    val risk_level: String,
    val relevance: List<String>,
    val story_zh: String,
)

@Serializable
data class PRSScores(
    val t2d: Double,
    val cvd: Double,
    val masld: Double,
)

@Serializable
data class GenomicsDemoPanel(
    val is_demo: Boolean,
    val prs: PRSScores,
    val summary: String,
    val variants: List<GeneVariant>,
)

@Serializable
data class MicrobiomeTaxon(
    val name: String,
    val key: String,
    val relative_abundance: Double,
    val reference: String,
    val status: String,
    val relevance: List<String>,
    val story_zh: String,
)

@Serializable
data class MicrobiomeDemoPanel(
    val is_demo: Boolean,
    val shannon: Double,
    val scfa_producer_pct: Double,
    val summary: String,
    val taxa: List<MicrobiomeTaxon>,
)

@Serializable
data class OmicsTriadInsight(
    val is_demo: Boolean,
    val metabolomics_score: Double,
    val cgm_score: Double,
    val heart_score: Double,
    val overlap_score: Double,
    val insights: List<String>,
)
