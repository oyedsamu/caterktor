plugins {
    id("caterktor.kmp")
    id("caterktor.publishing")
}

kotlin {
    // Disable the default hierarchy template so our custom nonJsMain intermediate
    // source set can be wired without KGP conflict warnings.
    applyDefaultHierarchyTemplate {
        common {
            group("nonJs") {
                withJvm()
                group("native") {
                    withApple()
                    withLinux()
                }
            }
            withJs()
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":caterktor-core"))
            api(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
        }

        jsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
    }
}
