import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.lwjgl.Lwjgl.Preset
import org.lwjgl.Release
import org.lwjgl.lwjgl
import org.lwjgl.sonatype

plugins {
    kotlin("jvm")
    id("org.lwjgl.plugin")
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    sonatype()
}

dependencies {
    lwjgl {
        version = Release.`3․3․4`
        implementation(Preset.minimalOpenGL)
    }
    implementation("org.joml:joml:1.10.5")
    implementation("de.articdive:jnoise-pipeline:4.1.0")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "21"
}

application {
    mainClass.set("MainKt")
    applicationDefaultJvmArgs = listOf("-XstartOnFirstThread", "-Djoml.nounsafe=true")
}
