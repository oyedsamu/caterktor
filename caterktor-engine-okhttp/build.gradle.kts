plugins {
    id("caterktor.kmp.jvm-only")
    id("caterktor.android")
    id("caterktor.publishing")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":caterktor-ktor"))
            api(libs.ktor.client.okhttp)
        }
    }
}
