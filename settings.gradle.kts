import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

rootProject.name = "component-investigation"

// The rd protocol model lives in a dedicated subproject so it can consume the Rider model
// jar and run rdgen before the frontend/backend are compiled.
include("protocol")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
    }
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.2.20"
        id("org.jetbrains.changelog") version "2.5.0"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("org.jetbrains.intellij.platform.settings") version "2.16.0"
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        // rd-gen + rider model dependencies are served from the JetBrains cache redirector.
        maven("https://cache-redirector.jetbrains.com/intellij-dependencies")

        // IntelliJ Platform Gradle Plugin Repositories Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
        intellijPlatform {
            defaultRepositories()
        }
    }
}
