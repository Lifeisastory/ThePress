plugins {
    id("thepress.android.library")
}

android {
    namespace = "com.aster.core.data"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:analytics"))
    implementation(libs.androidx.core.ktx)
    implementation(project(":core:network"))
    implementation(project(":core:localdb"))
    implementation(project(":core:localsp"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
}
