plugins {
    id("thepress.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.aster.feature.saved.api"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    api(project(":core:navigation"))
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}
