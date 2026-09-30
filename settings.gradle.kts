pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        exclusiveContent {
            forRepository {
                maven("https://repo.alessiodp.com/releases/") { name = "Libby" }
            }
            filter { includeGroup("net.byteflux") }
        }
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") {
            name = "SpigotMC"
        }
    }
}

rootProject.name = "DataVault"

include("api", "plugin")
