plugins {
    // Loaded here (but not applied) to put `KotlinMultiplatformExtension` on
    // the root project's buildscript classloader. The binary-compatibility
    // validator plugin, also applied at the root, reaches for that type when
    // it reacts to subprojects that declare it. Without this line BCV throws
    // `NoClassDefFoundError: org/jetbrains/kotlin/gradle/dsl/KotlinMultiplatformExtension`
    // because the Kotlin plugin is isolated in the subproject classloader.
    alias(libs.plugins.kotlin.multiplatform) apply false

    alias(libs.plugins.binary.compatibility.validator)
}

allprojects {
    group = "io.github.oyedsamu"
    version = "0.1.0-SNAPSHOT"
}

apiValidation {
    // When the Core Pipeline Engineer introduces
    // `io.github.oyedsamu.caterktor.ExperimentalCaterktor`, BCV will honor it
    // as a non-public marker and exclude annotated surfaces from the public dump.
    // BCV tolerates missing markers at config time — they are resolved when
    // `apiCheck` actually runs.
    nonPublicMarkers += "io.github.oyedsamu.caterktor.ExperimentalCaterktor"
}
