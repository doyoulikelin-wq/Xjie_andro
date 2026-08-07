import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
}

// Read non-secret build inputs from local.properties or the process environment.
// Signing credentials must never be checked in.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun buildInput(name: String): String? =
    localProps.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }
        ?: System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }

val devApiBaseUrl: String = buildInput("API_BASE_URL_DEBUG")
    ?: "http://10.0.2.2:8000"
val prodApiBaseUrl: String = buildInput("API_BASE_URL_RELEASE").orEmpty()
val releaseStoreFilePath = buildInput("XJIE_RELEASE_STORE_FILE")
val releaseStorePassword = buildInput("XJIE_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = buildInput("XJIE_RELEASE_KEY_ALIAS")
val releaseKeyPassword = buildInput("XJIE_RELEASE_KEY_PASSWORD")
val releaseSigningConfigured = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.xjie.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.xjie.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 2
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = rootProject.file(requireNotNull(releaseStoreFilePath))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            buildConfigField("String", "API_BASE_URL", "\"$devApiBaseUrl\"")
            buildConfigField("Boolean", "ALLOW_CLEARTEXT", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "API_BASE_URL", "\"$prodApiBaseUrl\"")
            buildConfigField("Boolean", "ALLOW_CLEARTEXT", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
            )
        }
    }
}

val verifyReleaseConfiguration by tasks.registering {
    group = "verification"
    description = "Reject release artifacts without an explicit HTTPS API and external signing inputs."
    doLast {
        check(
            prodApiBaseUrl.startsWith("https://") &&
                !prodApiBaseUrl.contains("example.com", ignoreCase = true),
        ) {
            "API_BASE_URL_RELEASE must be an explicit non-placeholder HTTPS URL"
        }
        check(releaseSigningConfigured) {
            "Release signing inputs are incomplete; provide XJIE_RELEASE_STORE_FILE, " +
                "XJIE_RELEASE_STORE_PASSWORD, XJIE_RELEASE_KEY_ALIAS, and XJIE_RELEASE_KEY_PASSWORD"
        }
        check(rootProject.file(requireNotNull(releaseStoreFilePath)).isFile) {
            "XJIE_RELEASE_STORE_FILE does not identify a regular keystore file"
        }
    }
}

tasks.configureEach {
    if (
        name == "assembleRelease" ||
        name == "bundleRelease" ||
        name == "packageRelease"
    ) {
        dependsOn(verifyReleaseConfiguration)
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.foundation)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Storage
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    // Image
    implementation(libs.coil.compose)

    // Charts
    implementation(libs.vico.compose.m3)
    implementation(libs.vico.compose)
    implementation(libs.vico.core)

    // Permissions
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.health.connect.client)

    // Logging
    implementation(libs.timber)

    // ML Kit text recognition (Chinese)
    implementation(libs.mlkit.text.recognition.chinese)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
