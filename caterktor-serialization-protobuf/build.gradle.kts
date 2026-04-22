plugins {
    id("caterktor.kmp")
    id("caterktor.android")
    id("caterktor.publishing")
    id("caterktor.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":caterktor-core"))
            api(libs.kotlinx.serialization.protobuf)
        }
    }
}
