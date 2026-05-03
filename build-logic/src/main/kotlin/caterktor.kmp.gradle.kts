/**
 * Full KMP target matrix for CaterKtor modules (PRD v2 §7, PRD-v2-execution F5).
 *
 * Targets: jvm, js(IR), ios{Arm64,SimulatorArm64,X64}, macos{Arm64,X64}, linux{X64,Arm64}.
 *
 * Android is applied separately via the `caterktor.android` convention — only
 * modules that opt into it pay the AGP configuration cost.
 *
 * Applies `kotlin("multiplatform")`, `explicitApi()`, and the shared
 * `applyCaterktorKmpCommon()` wiring (toolchain, JVM target, test deps).
 */
plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    js(IR) {
        browser()
        nodejs()
    }

    iosArm64()
    iosSimulatorArm64()
    iosX64()

    macosArm64()
    macosX64()

    linuxX64()
    linuxArm64()
}

applyCaterktorKmpCommon()
