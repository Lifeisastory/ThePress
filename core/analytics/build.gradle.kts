plugins {
    id("thepress.android.library")
}

android {
    namespace = "com.aster.core.analytics"
}

dependencies {
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
