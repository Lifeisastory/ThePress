plugins {
    id("thepress.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.aster.core.network"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    api(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    // 客户端配置、Service 注解和 JSON 类型属于模块的公开使用方式
    api(libs.okhttp)
    api(libs.retrofit)
    api(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.jsoup)
    implementation(libs.retrofit.converter.scalars)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
}

// 本地 JVM 测试没有 Android Application/AssetManager；Cookie 域校验需要 JVM 版的公共后缀资源
// 应用运行时仍使用 OkHttp Android 变体及其 AndroidX Startup 初始化器
configurations.matching { it.name.endsWith("UnitTestRuntimeClasspath") }.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute(module("com.squareup.okhttp3:okhttp"))
            .using(module("com.squareup.okhttp3:okhttp-jvm:${libs.versions.okhttp.get()}"))
    }
}
