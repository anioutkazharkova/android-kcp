package com.azharkova.kcp.plugin

import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption


class KcpGradlePlugin : KotlinCompilerPluginSupportPlugin {

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true


    override fun getCompilerPluginId(): String = "com.azharkova.kcp.plugin.kcp_plugin"

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = "com.azharkova.kcp.plugin",
        artifactId = "kcp_plugin",
        version = "0.1.2"
    )
    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val gradleExtension = kotlinCompilation.target.project.extensions.findByType(KcpPluginGradleExtension::class.java) ?: KcpPluginGradleExtension()

        return kotlinCompilation.target.project.provider {
            val options:List<SubpluginOption> = mutableListOf()//(SubpluginOption("enabled", gradleExtension.enabled.toString()))
            options
        }
    }

}