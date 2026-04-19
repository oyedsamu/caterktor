import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

/**
 * Shared, configuration-cache-safe KMP wiring used by every `caterktor.kmp*` convention.
 *
 * - Enables `explicitApi()` (PRD v2 §9: zero `@Suppress` in public API).
 * - Pins the Kotlin JVM target to 11 on every compilation that has one.
 * - Wires the JVM toolchain to 11 (foojay resolver is enabled at the root).
 * - Sets up `commonTest` with `kotlin("test")` and `kotlinx-coroutines-test`.
 *
 * Individual target selection (full matrix vs jvm-only vs apple-only vs jvm-linux)
 * lives in each convention plugin so consumers pick by plugin id, not by DSL.
 */
internal fun Project.applyCaterktorKmpCommon() {
    val kmp = extensions.getByType(KotlinMultiplatformExtension::class)

    kmp.explicitApi = ExplicitApiMode.Strict

    // JVM toolchain 11 — applies to every JVM-producing target in the module.
    kmp.jvmToolchain(11)

    // Pin Kotlin JVM target on any compilation that has one. Using the
    // `KotlinCompilationTask` surface keeps us off the deprecated
    // `KotlinCompile.kotlinOptions` path and is configuration-cache safe.
    tasks.withType(KotlinCompilationTask::class.java).configureEach {
        val opts = compilerOptions
        if (opts is KotlinJvmCompilerOptions) {
            opts.jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    // commonTest essentials, via version catalog.
    val libs = extensions.getByType(org.gradle.api.artifacts.VersionCatalogsExtension::class)
        .named("libs")
    kmp.sourceSets.getByName("commonTest").dependencies {
        implementation(kotlin("test"))
        implementation(libs.findLibrary("kotlinx-coroutines-test").get())
    }
}
