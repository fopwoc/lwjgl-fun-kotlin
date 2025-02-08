import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.lwjgl.Lwjgl
import org.lwjgl.Release
import org.lwjgl.lwjgl
import org.lwjgl.sonatype

val kotlinVersion: String by project
val javaVersion: String by project

plugins {
    kotlin("jvm")
    id("org.lwjgl.plugin")
    application
    id("org.graalvm.buildtools.native")

}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    sonatype()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }
}

graalvmNative {
    toolchainDetection.set(true)
    binaries {
        named("main") {
            useFatJar.set(true)
            imageName.set("my-app")
            mainClass.set("io.github.fopwoc.MainKt")
            buildArgs.add("-O4")
            javaLauncher.set(
                javaToolchains.launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(javaVersion))
                    vendor.set(JvmVendorSpec.matching("Oracle Corporation"))
                }
            )
        }
//        named("test") {
//            buildArgs.add("-O0")
//        }
    }
//    binaries.all {
//        buildArgs.add("--verbose")
//    }
}

dependencies {
    lwjgl {
        version = Release.`3․3․4`
        implementation(Lwjgl.Preset.minimalVulkan + Lwjgl.Module.shaderc + Lwjgl.Module.assimp + Lwjgl.Module.stb + Lwjgl.Module.vma)
    }
    implementation("org.joml:joml:1.10.5")
    implementation("org.tinylog:tinylog:1.3.6")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

