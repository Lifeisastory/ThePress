plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "thepress.android.application"
            // 具体实现这个插件的 Kotlin 类
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "thepress.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
    }
}