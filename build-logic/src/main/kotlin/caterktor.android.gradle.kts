/**
 * Layers the AGP KMP Library plugin on top of a `caterktor.kmp*` convention
 * so modules can opt into an Android target independently of the rest of the
 * KMP target matrix. Every CaterKtor Android artifact ships with:
 *
 * - `compileSdk = 36`, `minSdk = 23` (PRD v2 §7 floor)
 * - a namespace derived from the module name, rooted at
 *   `io.github.oyedsamu.caterktor.<modulePart>` (matches the project group)
 *
 * Individual modules may override the namespace after applying this convention.
 *
 * This convention relies on a `caterktor.kmp*` convention already being
 * applied in the same `plugins { }` block so the Kotlin Multiplatform
 * extension is available when `androidLibrary { }` is invoked.
 */
plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

// Derive a stable, valid Android namespace from the Gradle module name.
// `caterktor-engine-okhttp` -> `io.github.oyedsamu.caterktor.engine.okhttp`.
val derivedNamespace: String = "io.github.oyedsamu.caterktor." +
    project.name.removePrefix("caterktor-").replace('-', '.')

kotlin {
    androidLibrary {
        namespace = derivedNamespace
        compileSdk = 36
        minSdk = 23
    }
}
