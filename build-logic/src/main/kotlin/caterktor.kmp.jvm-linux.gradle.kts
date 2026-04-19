/**
 * JVM + Linux KMP wiring for modules where that pairing is the entire story
 * (e.g. `caterktor-engine-cio`: CIO is Ktor's JVM/Linux engine).
 */
plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()

    linuxX64()
    linuxArm64()
}

applyCaterktorKmpCommon()
