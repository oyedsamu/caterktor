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
    // PREFER_SETTINGS allows Kotlin/JS toolchain setup (Node.js distribution
    // repository) to add project-level repositories without breaking the build.
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        // Required by Kotlin/JS to download the Node.js distribution used for tests.
        ivy {
            name = "Node.js"
            setUrl("https://nodejs.org/dist")
            patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
        // Required by Kotlin/JS for Yarn package manager used by browser targets.
        ivy {
            name = "Yarn"
            setUrl("https://github.com/yarnpkg/yarn/releases/download")
            patternLayout { artifact("v[revision]/[artifact](-v[revision]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.yarnpkg", "yarn") }
        }
    }
}

rootProject.name = "caterktor"

includeBuild("build-logic")

include(
    ":caterktor-core",
    ":caterktor-connectivity",
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
    ":caterktor-websocket",
    ":caterktor-sse",
    ":caterktor-sample",
    // caterktor-otel: reserved for Wave 12 OpenTelemetry integration.
    //   Excluded from the build graph until a stable KMP OTel SDK exists.
    //   Source lives under caterktor-otel/ for tracking purposes.
    // caterktor-ktorfit: reserved for Wave 13 declarative adapter.
    //   Excluded from the build graph until Ktorfit's KSP processor supports
    //   all 9 KMP targets.
)
