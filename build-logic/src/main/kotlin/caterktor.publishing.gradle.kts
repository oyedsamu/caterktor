/**
 * Minimal publishing stub. Sets group + version and populates a POM block with
 * the project identity every module shares. The actual Sonatype / Maven Central
 * wiring (signing, portal publisher, staging, bundles) is F3 territory.
 *
 * Intentionally does NOT:
 *  - apply the `signing` plugin
 *  - register Sonatype repositories
 *  - configure javadoc / dokka jars
 *
 * // TODO(F3): Maven Central publishing wired by F3 agent
 */
plugins {
    `maven-publish`
}

group = "io.github.oyedsamu"
version = "0.1.0-SNAPSHOT"

publishing {
    publications.withType<MavenPublication>().configureEach {
        // artifactId defaults to project.name (e.g. "caterktor-core"),
        // matching the coordinate shape io.github.oyedsamu:caterktor-<module>.
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
}
