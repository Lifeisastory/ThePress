plugins {
    id("thepress.android.library")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.aster.feature.webview.impl"

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.webkit)
    implementation(project(":core:design_system"))
    implementation(project(":feature:webview:api"))
}
