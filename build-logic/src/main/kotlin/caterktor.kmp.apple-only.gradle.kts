/**
 * Apple-only KMP wiring for modules that are inherently Darwin-scoped
 * (e.g. `caterktor-engine-darwin`). No JVM, no Android, no Linux.
 */
plugins {
    kotlin("multiplatform")
}

kotlin {
    iosArm64()
    iosSimulatorArm64()
    iosX64()

    macosArm64()
    macosX64()
}

applyCaterktorKmpCommon()
