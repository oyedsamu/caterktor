/**
 * JVM-only KMP wiring for modules whose implementation is JVM-bound (e.g.
 * `caterktor-engine-okhttp`, which additionally applies `caterktor.android`
 * since OkHttp is the preferred engine on JVM and Android).
 */
plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
}

applyCaterktorKmpCommon()
