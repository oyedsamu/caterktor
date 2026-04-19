plugins {
    kotlin("multiplatform") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    id("com.android.kotlin.multiplatform.library") version "8.13.2"
    id("maven-publish")
}

group = "com.byoyedele.caterktor"
version = "0.1.0-SNAPSHOT"

kotlin {
    android {
        namespace = "com.byoyedele.caterktor"
        compileSdk = 36
        minSdk = 23
    }
    jvm()

    sourceSets {
        commonMain.dependencies {
            api("io.ktor:ktor-client-core:3.4.0")
            api("io.ktor:ktor-http:3.4.0")
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
            implementation("io.ktor:ktor-client-mock:3.4.0")
        }
        androidMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:3.4.0")
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("CaterKtor")
            description.set("A small Kotlin networking abstraction backed by Ktor.")
            url.set("https://github.com/byoyedele/caterktor")
            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            developers {
                developer {
                    id.set("byoyedele")
                    name.set("Bolaji Oyedele")
                }
            }
            scm {
                connection.set("scm:git:https://github.com/byoyedele/caterktor.git")
                developerConnection.set("scm:git:ssh://git@github.com/byoyedele/caterktor.git")
                url.set("https://github.com/byoyedele/caterktor")
            }
        }
    }

    repositories {
        maven {
            name = "internal"
            url = uri(
                providers.gradleProperty("publishingUrl")
                    .orElse(providers.environmentVariable("PUBLISHING_URL"))
                    .orElse(layout.buildDirectory.dir("repo").map { it.asFile.absolutePath })
            )
            credentials {
                username = providers.gradleProperty("publishingUsername")
                    .orElse(providers.environmentVariable("PUBLISHING_USERNAME"))
                    .orNull
                password = providers.gradleProperty("publishingPassword")
                    .orElse(providers.environmentVariable("PUBLISHING_PASSWORD"))
                    .orNull
            }
        }
    }
}
