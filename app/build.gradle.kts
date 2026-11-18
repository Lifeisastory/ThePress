plugins {
    id("thepress.android.application")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.aster.thepress"

    defaultConfig {
        applicationId = "com.aster.thepress"
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(project(":core:design_system"))
    implementation(project(":core:data"))
    implementation(project(":core:analytics"))
    implementation(project(":core:localdb"))
    implementation(project(":core:network"))
    implementation(project(":core:navigation"))
    implementation(project(":feature:home:api"))
    implementation(project(":feature:home:impl"))
    implementation(project(":feature:search:api"))
    implementation(project(":feature:search:impl"))
    implementation(project(":feature:saved:api"))
    implementation(project(":feature:saved:impl"))
    implementation(project(":feature:me:api"))
    implementation(project(":feature:me:impl"))
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
