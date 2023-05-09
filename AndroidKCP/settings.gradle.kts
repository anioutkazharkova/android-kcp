pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenLocal()
        mavenCentral()
        maven(url = "https://jitpack.io")
        maven(url = "https://plugins.gradle.org/m2/")
        google()
        maven (url = "https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap")
    }
}

dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenLocal()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}
rootProject.name = "AndroidKCP"
include(":app")
include(":kcp_plugin")
include(":kcp_plugin_runtime")
//include(":kmm_plugin_native")
include(":kcp_plugin_gradle")

include(":annotations")
