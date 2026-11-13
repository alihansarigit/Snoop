pluginManagement {
    // The Snoop Gradle plugin lives in its own build so the sample can apply it by id,
    // exactly the way a published consumer would.
    includeBuild("gradle-plugin")
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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Snoop"
include(":core")
include(":okhttp")
include(":ktor")
include(":no-op")
include(":sample")
include(":daemon")
