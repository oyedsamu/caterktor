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
    version = providers.gradleProperty("caterktor.version").getOrElse("0.1.0-SNAPSHOT")
}

apiValidation {
    // @ExperimentalCaterktor types are exempt from the two-minor deprecation
    // window but they STILL appear in the API dump — see CONTRIBUTING.md.
    // Do NOT add ExperimentalCaterktor to nonPublicMarkers; doing so silently
    // drops the entire experimental surface from apiCheck, defeating BCV's
    // purpose for the bulk of the Wave 4–8 API.
}
