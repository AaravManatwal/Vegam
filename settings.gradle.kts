pluginManagement {
    repositories {
        google()
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

rootProject.name = "Vegam"

// Baaki modules (:core:download, :feature:home...) aage phases mein judenge.
include(":app")
include(":core:engine")
include(":core:database")
include(":core:download")
include(":core:ui")
include(":feature:home")
include(":feature:add")
include(":feature:smart")
include(":feature:detail")
include(":feature:stats")
include(":feature:settings")
include(":feature:onboarding")
