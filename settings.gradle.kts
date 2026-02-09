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
        maven { url = uri("https://artifactory.appodeal.com/appodeal") }
    }
}

rootProject.name = "dots-and-boxes"
include(":game")
include(":p2pkit")
include(":uikit")
include(":gridgame")
include(":mockpvp")

project(":p2pkit").projectDir = file("p2pkit")
project(":uikit").projectDir = file("uikit")
project(":gridgame").projectDir = file("gridgame")
project(":mockpvp").projectDir = file("mockpvp")
