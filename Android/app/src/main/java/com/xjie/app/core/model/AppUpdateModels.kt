package com.xjie.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppUpdateCheck(
    val platform: String,
    @SerialName("current_version") val currentVersion: String? = null,
    @SerialName("current_build") val currentBuild: Int? = null,
    @SerialName("latest_version") val latestVersion: String,
    @SerialName("latest_build") val latestBuild: Int,
    @SerialName("min_supported_build") val minSupportedBuild: Int,
    @SerialName("update_available") val updateAvailable: Boolean,
    val required: Boolean,
    @SerialName("force_update") val forceUpdate: Boolean,
    val title: String,
    val message: String,
    val changelog: String,
    @SerialName("download_url") val downloadUrl: String? = null,
    @SerialName("store_url") val storeUrl: String? = null,
    val sha256: String? = null,
) {
    val shouldForce: Boolean get() = required || forceUpdate
    val updateUrl: String? get() = downloadUrl?.takeIf { it.isNotBlank() } ?: storeUrl
}
