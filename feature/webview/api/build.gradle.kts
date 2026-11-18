plugins {
    id("thepress.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.aster.feature.webview.api"
}

dependencies {
    api(project(":core:navigation"))
    implementation(libs.kotlinx.serialization.core)
}
