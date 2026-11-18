plugins {
    id("thepress.android.library")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.aster.feature.search.impl"

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(project(":core:data"))
    implementation(project(":core:design_system"))
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":feature:search:api"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
