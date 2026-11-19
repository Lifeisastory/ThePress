plugins {
    id("thepress.android.library")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.aster.feature.main.impl"

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(project(":core:data"))
    implementation(project(":core:design_system"))
    implementation(project(":feature:main:api"))
    implementation(project(":feature:for_you:api"))
    implementation(project(":feature:for_you:impl"))
    implementation(project(":feature:digest:api"))
    implementation(project(":feature:digest:impl"))
    implementation(project(":feature:me:api"))
    implementation(project(":feature:me:impl"))
}
