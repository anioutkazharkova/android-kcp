// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    dependencies {
        classpath("com.android.tools.build:gradle:7.4.2")
    classpath("com.azharkova.kcp.plugin:kcp_plugin:0.1.2")
    }
}


plugins {
    //trick: for the same plugin versions in all sub-modules
    id("com.android.application").version("7.3.1").apply(false)
    id("com.android.library").version("7.3.1").apply(false)
    kotlin("android").version("1.8.0").apply(false)
    id("org.jetbrains.kotlin.jvm") version "1.8.0" apply false

   id("kcp_plugin") version "0.1.2" apply true
    id("org.javamodularity.moduleplugin") version "1.8.12" apply false
}
