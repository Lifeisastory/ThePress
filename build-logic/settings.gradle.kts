// 这份文件负责配置 build-logic 自己的仓库和版本目录
// build-logic 是独立构建，需要显式导入主项目的 TOML，才能使用同一套 libs.xxx

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }

    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"