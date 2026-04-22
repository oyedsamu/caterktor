plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.serialization.plugin)
    implementation(libs.android.gradle.plugin)
    implementation(libs.android.kmp.library.plugin)
    implementation(libs.bcv.plugin)
    implementation(libs.dokka.plugin)
    implementation(libs.vanniktech.publish.plugin)
}
