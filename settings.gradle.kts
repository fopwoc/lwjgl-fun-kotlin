rootProject.name = "lwjgl-fun-kotlin"

pluginManagement {
    val kotlinVersion: String by settings

    plugins {
        kotlin("jvm") version kotlinVersion apply false
        id("org.lwjgl.plugin") version "0.0.34" apply false
        id("org.graalvm.buildtools.native") version "0.9.28" apply false
        //id("com.github.johnrengelman.shadow") version "8.1.1" apply false
    }

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

include(
    ":opengl",
    ":vulkan",
)
