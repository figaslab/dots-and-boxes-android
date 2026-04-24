pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// Single source of truth: lib-versions.properties declares which version of
// each submodule Gradle resolves from GitHub Packages. Loaded here so both
// local builds and CI see the same versions via findProperty(...).
val libVersions = java.util.Properties().apply {
    file("lib-versions.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
gradle.beforeProject {
    libVersions.forEach { (key, value) ->
        val name = key.toString()
        if (!project.hasProperty(name)) {
            project.extensions.extraProperties.set(name, value)
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Local mirror of Appodeal transitive deps - first, offline-friendly.
        // Populated by ../appodeal-mirror/sync. Misses fall through to upstream.
        maven { url = uri("file:///${rootProject.projectDir.parentFile}/appodeal-mirror/repository") }
        maven { url = uri("https://artifactory.appodeal.com/appodeal") }
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

rootProject.name = "dots-and-boxes"
include(":game")
