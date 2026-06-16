import com.jetbrains.rd.generator.gradle.RdGenExtension
import com.jetbrains.rd.generator.gradle.RdGenPlugin
import com.jetbrains.rd.generator.gradle.RdGenTask

buildscript {
    repositories {
        maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
        mavenCentral()
    }
    dependencies {
        classpath("com.jetbrains.rd:rd-gen:${providers.gradleProperty("rdGenVersion").get()}")
    }
}

plugins {
    kotlin("jvm")
}

// The rd-gen jar doesn't register a plugin id marker, so apply it by class.
apply<RdGenPlugin>()

// Pin Java + Kotlin to the same, supported JVM (21). Foojay auto-provisions the JDK, so the
// build doesn't depend on whatever java/JBR is launching Gradle (e.g. JDK 26).
kotlin {
    jvmToolchain(21)
}

repositories {
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
    mavenCentral()
}

// Consume the Rider model jar exposed by the root project (see root build.gradle.kts).
val riderModel: Configuration by configurations.creating { isCanBeConsumed = false }

dependencies {
    // Global kotlin.stdlib.default.dependency=false (for the platform frontend) means this
    // plain Kotlin module must pull the stdlib itself, or kotlin.* built-ins are unavailable.
    implementation(kotlin("stdlib"))
    implementation("com.jetbrains.rd:rd-gen:${providers.gradleProperty("rdGenVersion").get()}")
    riderModel(project(mapOf("path" to ":", "configuration" to "riderModel")))
    implementation(files({ riderModel.files }))
}

val modelDir = layout.projectDirectory.dir("src/main/kotlin/model")
// Kotlin model -> frontend (root project's generated source set).
val ktOutput = rootProject.layout.buildDirectory.dir("generated/model/com/componentinvestigation/model")
// C# model -> backend project.
val csOutput = rootProject.layout.projectDirectory.dir("src/dotnet/ComponentInvestigation.Rider/Model/Generated")

configure<RdGenExtension> {
    verbose = true
    packages = "model"

    generator {
        language = "kotlin"
        transform = "asis"
        root = "com.jetbrains.rider.model.nova.ide.IdeRoot"
        namespace = "com.componentinvestigation.model"
        directory = ktOutput.get().asFile.absolutePath
    }

    generator {
        language = "csharp"
        transform = "reversed"
        root = "com.jetbrains.rider.model.nova.ide.IdeRoot"
        namespace = "ComponentInvestigation.Rider.Model"
        directory = csOutput.asFile.absolutePath
    }
}

tasks.withType<RdGenTask> {
    val classPath = sourceSets["main"].runtimeClasspath
    dependsOn(classPath)
    classpath(classPath)
}
