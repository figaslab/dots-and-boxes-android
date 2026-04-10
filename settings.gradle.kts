pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
val usePrebuiltLibs: Boolean = providers.gradleProperty("usePrebuiltLibs")
    .getOrElse("false").toBoolean()

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://artifactory.appodeal.com/appodeal") }
        if (usePrebuiltLibs) {
            listOf("p2pkit-android", "gridgame-android", "uikit-android", "mockpvp-android").forEach { repo ->
                maven {
                    url = uri("https://maven.pkg.github.com/figaslab/$repo")
                    credentials {
                        username = System.getenv("GITHUB_ACTOR") ?: ""
                        password = System.getenv("GITHUB_TOKEN") ?: ""
                    }
                }
            }
        }
    }
}

rootProject.name = "dots-and-boxes"
include(":game")

if (!usePrebuiltLibs) {
    include(":p2pkit")
    include(":uikit")
    include(":gridgame")
    include(":mockpvp")
    project(":p2pkit").projectDir = file("p2pkit")
    project(":uikit").projectDir = file("uikit")
    project(":gridgame").projectDir = file("gridgame")
    project(":mockpvp").projectDir = file("mockpvp")
}
