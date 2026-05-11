rootProject.name = "Xjie"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven("https://developer.huawei.com/repo/")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Huawei HMS Push
        maven("https://developer.huawei.com/repo/")
    }
}

include(":app")
