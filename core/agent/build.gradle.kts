plugins { id("thepress.android.library") }

android { namespace = "com.aster.core.agent" }

dependencies {
    api(project(":core:model"))
    api(project(":core:analytics"))
    implementation(libs.okhttp)
    api(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
}
