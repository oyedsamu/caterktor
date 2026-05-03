import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.jetbrains.dokka.gradle.tasks.DokkaBaseTask

plugins {
    // Loaded here (but not applied) to put `KotlinMultiplatformExtension` on
    // the root project's buildscript classloader. The binary-compatibility
    // validator plugin, also applied at the root, reaches for that type when
    // it reacts to subprojects that declare it. Without this line BCV throws
    // `NoClassDefFoundError: org/jetbrains/kotlin/gradle/dsl/KotlinMultiplatformExtension`
    // because the Kotlin plugin is isolated in the subproject classloader.
    alias(libs.plugins.kotlin.multiplatform) apply false

    alias(libs.plugins.binary.compatibility.validator)

    // Applied at root so `./gradlew dokkaGenerate` produces a single aggregated
    // HTML site across all submodules. Submodule Dokka plugins are applied by
    // the convention plugin below; the root just owns the aggregation task.
    alias(libs.plugins.dokka)
}

val documentedProjects = listOf(
    "caterktor-core",
    "caterktor-connectivity",
    "caterktor-ktor",
    "caterktor-serialization-json",
    "caterktor-serialization-protobuf",
    "caterktor-serialization-cbor",
    "caterktor-engine-okhttp",
    "caterktor-engine-darwin",
    "caterktor-engine-cio",
    "caterktor-logging",
    "caterktor-auth",
    "caterktor-testing",
    "caterktor-websocket",
    "caterktor-sse",
)

dependencies {
    documentedProjects.forEach { moduleName ->
        dokka(project(":$moduleName"))
    }
}

dokka {
    moduleName.set("CaterKtor")
    moduleVersion.set(project.version.toString())

    dokkaPublications.named("html") {
        includes.from(layout.projectDirectory.file("docs/dokka/root.md"))
    }
}

tasks.withType<DokkaBaseTask>().configureEach {
    notCompatibleWithConfigurationCache("Dokka 2.0.0 aggregate tasks retain Gradle Project references.")
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
    ignoredProjects.add("caterktor-sample")
}

val validateDokkaPublication by tasks.registering {
    group = "verification"
    description = "Fails if the root Dokka publication is missing aggregated module documentation."

    dependsOn("dokkaGeneratePublicationHtml")

    val outputDir = layout.buildDirectory.dir("dokka/html")
    val requiredPages = listOf(
        "index.html",
        "caterktor-core/io.github.oyedsamu.caterktor/index.html",
        "caterktor-connectivity/io.github.oyedsamu.caterktor.connectivity/index.html",
        "caterktor-ktor/io.github.oyedsamu.caterktor/index.html",
        "caterktor-auth/io.github.oyedsamu.caterktor.auth/index.html",
        "caterktor-logging/io.github.oyedsamu.caterktor.logging/index.html",
        "caterktor-testing/io.github.oyedsamu.caterktor.testing/index.html",
        "caterktor-serialization-json/io.github.oyedsamu.caterktor.serialization.json/index.html",
        "caterktor-serialization-protobuf/io.github.oyedsamu.caterktor.serialization.protobuf/index.html",
        "caterktor-serialization-cbor/io.github.oyedsamu.caterktor.serialization.cbor/index.html",
        "caterktor-engine-okhttp/io.github.oyedsamu.caterktor.engine.okhttp/index.html",
        "caterktor-engine-darwin/io.github.oyedsamu.caterktor.engine.darwin/index.html",
        "caterktor-engine-cio/io.github.oyedsamu.caterktor.engine.cio/index.html",
        "caterktor-websocket/io.github.oyedsamu.caterktor.websocket/index.html",
        "caterktor-sse/io.github.oyedsamu.caterktor.sse/index.html",
    )
    val requiredSymbols = listOf(
        "NetworkClient",
        "KtorTransport",
        "AuthRefreshInterceptor",
        "LoggerInterceptor",
        "FakeTransport",
        "CaterktorHttpServer",
        "ConnectivityInterceptor",
        "WebSocketFrame",
        "SseEvent",
    )

    doLast {
        val docsDir = outputDir.get().asFile
        val missingPages = requiredPages.filter { relativePath ->
            !docsDir.resolve(relativePath).isFile
        }

        if (missingPages.isNotEmpty()) {
            throw GradleException(
                "Dokka publication is incomplete. Missing pages:\n" +
                    missingPages.joinToString(separator = "\n") { " - $it" }
            )
        }

        val searchIndex = docsDir.resolve("scripts/pages.json")
        if (!searchIndex.isFile) {
            throw GradleException("Dokka publication is incomplete. Missing scripts/pages.json.")
        }

        val searchText = searchIndex.readText()
        val missingSymbols = requiredSymbols.filterNot(searchText::contains)
        if (missingSymbols.isNotEmpty()) {
            throw GradleException(
                "Dokka publication search index is missing expected API symbols: " +
                    missingSymbols.joinToString()
            )
        }
    }
}

subprojects {
    tasks.withType<AbstractPublishToMaven>().configureEach {
        dependsOn(rootProject.tasks.named("validateDokkaPublication"))
    }
}
