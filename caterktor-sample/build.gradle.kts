@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)

plugins {
    id("caterktor.kmp.jvm-only")
    id("caterktor.serialization")
    id("org.jetbrains.dokka")
}

kotlin {
    jvm {
        mainRun {
            mainClass.set("io.github.oyedsamu.caterktor.sample.MainKt")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":caterktor-core"))
            implementation(project(":caterktor-auth"))
            implementation(project(":caterktor-logging"))
            implementation(project(":caterktor-serialization-json"))
            implementation(project(":caterktor-testing"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

tasks.named("check") {
    dependsOn("jvmRun")
}
