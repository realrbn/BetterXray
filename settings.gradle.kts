pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.neoforged.net/releases")
        maven("https://maven.minecraftforge.net/")
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
    id("dev.kikugie.loom-back-compat") version "0.4.2"
}

stonecutter {
    create(rootProject) {
        versions("1.20.1", "1.21.1", "1.21.11", "26.2", "26.3")
        version("1.20.1-forge", "1.20.1").buildscript("build.forge-legacy.gradle.kts")
        version("1.21.1-forge", "1.21.1").buildscript("build.forge.gradle")
        version("1.21.1-neoforge", "1.21.1").buildscript("build.neoforge.gradle.kts")
        version("1.21.11-neoforge", "1.21.11").buildscript("build.neoforge.gradle.kts")
        version("26.2-neoforge", "26.2").buildscript("build.neoforge.gradle.kts")
        version("26.3-neoforge", "26.3").buildscript("build.neoforge.gradle.kts")
        vcsVersion = "26.3"
    }
}

rootProject.name = "betterxray"
