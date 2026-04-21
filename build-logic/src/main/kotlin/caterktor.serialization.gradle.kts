/**
 * Applies the Kotlin serialization compiler plugin to a CaterKtor module.
 *
 * Use this convention alongside `caterktor.kmp` (or any other KMP variant)
 * for modules that declare `@Serializable` types or depend on
 * `kotlinx-serialization-*` artifacts.
 */
plugins {
    id("org.jetbrains.kotlin.plugin.serialization")
}
