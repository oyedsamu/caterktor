pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "caterktor"

includeBuild("build-logic")

include(
    ":caterktor-core",
    ":caterktor-ktor",
    ":caterktor-serialization-json",
    ":caterktor-serialization-protobuf",
    ":caterktor-serialization-cbor",
    ":caterktor-engine-okhttp",
    ":caterktor-engine-darwin",
    ":caterktor-engine-cio",
    ":caterktor-logging",
    ":caterktor-auth",
    ":caterktor-testing",
    ":caterktor-sample",
    // caterktor-otel: reserved for Wave 12 OpenTelemetry integration.
    //   Excluded from the build graph until a stable KMP OTel SDK exists.
    //   Source lives under caterktor-otel/ for tracking purposes.
    // caterktor-ktorfit: reserved for Wave 13 declarative adapter.
    //   Excluded from the build graph until Ktorfit's KSP processor supports
    //   all 9 KMP targets.
)
