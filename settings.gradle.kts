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

rootProject.name = "BaseHaptic"

include(":mobile")
project(":mobile").projectDir = file("apps/mobile/app")

include(":watch")
project(":watch").projectDir = file("apps/watch/app")
