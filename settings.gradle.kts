
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "AURA"
include(":app")

val buildDirLocation = java.io.File(System.getProperty("user.home"), ".gradle_builds/auraa")
gradle.beforeProject {
    val sub = if (path == ":") "root" else path.replace(":", "")
    layout.buildDirectory.set(java.io.File(buildDirLocation, sub))
}

