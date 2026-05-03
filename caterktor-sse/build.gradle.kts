plugins {
    id("caterktor.kmp")
    id("caterktor.publishing")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":caterktor-ktor"))
        }
        val jvmTest by getting {
            dependencies {
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.server.cio)
                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.sse)
            }
        }
    }
}
