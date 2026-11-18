pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ThePress"
include(":app")
include(":core:design_system")
include(":core:ui")
include(":core:data")
include(":core:analytics")
include(":core:localdb")
include(":core:localsp")
include(":core:network")
include(":core:navigation")
include(":feature:main:api")
include(":feature:main:impl")
include(":feature:home:api")
include(":feature:home:impl")
include(":feature:search:api")
include(":feature:search:impl")
include(":feature:webview:api")
include(":feature:webview:impl")
include(":feature:saved:api")
include(":feature:saved:impl")
include(":feature:me:api")
include(":feature:me:impl")
include(":core:model")
