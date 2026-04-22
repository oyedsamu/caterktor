plugins {
    id("caterktor.kmp.jvm-linux")
    id("caterktor.publishing")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":caterktor-ktor"))
            api(libs.ktor.client.cio)
        }
    }
}
