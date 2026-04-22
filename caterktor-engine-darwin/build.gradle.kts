plugins {
    id("caterktor.kmp.apple-only")
    id("caterktor.publishing")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":caterktor-ktor"))
            api(libs.ktor.client.darwin)
        }
    }
}
