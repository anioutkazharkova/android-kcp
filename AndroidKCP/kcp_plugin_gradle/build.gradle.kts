import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java-gradle-plugin")
    kotlin("jvm")
    id("maven-publish")
}

group = "com.azharkova.kcp.plugin"
version = "0.1.2"

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("gradle-plugin-api"))
}


tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

gradlePlugin {
    plugins {
        create("kcp_plugin") {
            id = "kcp_plugin"
            displayName = "Kotlin Debug Log compiler plugin"
            description = "Kotlin compiler plugin to add debug logging to functions"
            implementationClass = "com.azharkova.kcp.plugin.KcpGradlePlugin"
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("default") {
            from(components["java"])
            artifact(tasks.kotlinSourcesJar)
        }
    }
}
