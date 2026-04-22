/**
 * Convention plugin that wires every CaterKtor module into Maven Central via the
 * com.vanniktech.maven.publish plugin (New Central Portal, post-2024).
 *
 * Publishing targets:
 *  - Maven Central (CENTRAL_PORTAL) — manual release, automaticRelease = false
 *  - Maven Local (publishToMavenLocal, no signing required)
 *
 * Signing — conditional on env var / project property so local builds work
 * without GPG configured.  CI injects:
 *   ORG_GRADLE_PROJECT_signingInMemoryKey
 *   ORG_GRADLE_PROJECT_signingInMemoryKeyPassword
 *
 * ## No-placeholder gate
 *
 * Every publish task depends on [validatePublicationReadiness], which fails
 * fast if this module's BCV `.api` dump is empty or consists only of blank
 * lines. This ensures we never publish a stub artifact to Maven Central.
 *
 * DO NOT change automaticRelease without TL sign-off.
 */
plugins {
    id("com.vanniktech.maven.publish")
    id("org.jetbrains.dokka")
}

// ---------------------------------------------------------------------------
// No-placeholder publication gate
// ---------------------------------------------------------------------------
// Fails the build if the module's BCV API dump is absent or empty, which
// indicates a placeholder-only module was accidentally wired into publishing.
val validatePublicationReadiness by tasks.registering {
    group = "verification"
    description = "Fails if this module has no real public API (placeholder guard)."

    // Capture at configuration time — safe for configuration cache.
    val apiDir = layout.projectDirectory.dir("api")
    val moduleName = name

    doLast {
        val apiDirFile = apiDir.asFile

        // If the api/ directory does not exist on this host, the module is
        // likely a platform-specific module (e.g. Apple-only) being validated
        // on a non-host platform. The apiCheck task handles cross-platform
        // validation on the correct host. Skip here.
        if (!apiDirFile.exists()) return@doLast

        val apiFiles = apiDirFile.walkTopDown()
            .filter { it.isFile && (it.extension == "api" || it.name.endsWith(".klib.api")) }
            .toList()

        if (apiFiles.isEmpty()) return@doLast // Same: no dump on this host, handled by apiCheck.

        val hasRealApi = apiFiles.any { file ->
            file.readLines().any { line ->
                val trimmed = line.trim()
                trimmed.isNotEmpty() && !trimmed.startsWith("//")
            }
        }

        if (!hasRealApi) {
            throw GradleException(
                "Publication gate: '$moduleName' API dump contains no public declarations. " +
                    "A placeholder-only module must not be published. " +
                    "Either implement the module or remove caterktor.publishing from its build.gradle.kts."
            )
        }
    }
}

// Wire the gate before every publish variant so it runs regardless of which
// publish task is invoked (publishToMavenLocal, publishToMavenCentral, etc.).
tasks.withType<AbstractPublishToMaven>().configureEach {
    dependsOn(validatePublicationReadiness)
}

group = "io.github.oyedsamu"
version = (project.findProperty("caterktor.version") ?: project.version).toString()

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL, automaticRelease = false)

    val isSigningRequired = project.hasProperty("signingInMemoryKey") ||
        System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey") != null

    if (isSigningRequired) {
        signAllPublications()
    }

    pom {
        name.set(project.name)
        description.set(
            "CaterKtor — an opinionated application networking layer on top of Ktor 3.x for Kotlin Multiplatform."
        )
        url.set("https://github.com/oyedsamu/caterktor")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("oyedsamu")
                name.set("Samuel Oyedele")
            }
        }
        scm {
            connection.set("scm:git:https://github.com/oyedsamu/caterktor.git")
            developerConnection.set("scm:git:ssh://git@github.com/oyedsamu/caterktor.git")
            url.set("https://github.com/oyedsamu/caterktor")
        }
    }
}
