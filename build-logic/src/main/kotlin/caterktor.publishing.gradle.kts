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
 * DO NOT change automaticRelease without TL sign-off.
 */
plugins {
    id("com.vanniktech.maven.publish")
}

group = "io.github.oyedsamu"
version = project.findProperty("version") ?: "0.1.0-SNAPSHOT"

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
