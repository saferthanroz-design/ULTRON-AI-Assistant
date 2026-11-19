pluginManagement {
    resolutionStrategy { eachPlugin {
        when (requested.id.id) {
            "com.android.application" -> useModule("com.android.tools.build:gradle:${requested.version}")
            "org.jetbrains.kotlin.android" -> useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
            "org.jetbrains.kotlin.plugin.compose" -> useModule("org.jetbrains.kotlin:compose-compiler-gradle-plugin:${requested.version}")
        }
    } }
    repositories {
        google { content { includeGroupByRegex("com\\.android(\\..*)?"); includeGroupByRegex("com\\.google\\.testing(\\..*)?"); includeGroupByRegex("androidx(\\..*)?") } }
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        google { content { includeGroupByRegex("com\\.android(\\..*)?"); includeGroupByRegex("androidx(\\..*)?") } }
        mavenCentral()
    }
}
rootProject.name = "FitLog"
include(":app")
