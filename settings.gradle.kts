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
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            authentication {
                create<BasicAuthentication>("basic")
            }
            credentials {
                // Do not change the username
                username = "mapbox"
                // Password is the Mapbox API key stored in gradle.properties
                password = providers.gradleProperty("MAPBOX_DOWNLOAD_TOKEN").orNull
            }
        }
    }
}

rootProject.name = "ITS Visualizer"
include(":app")
 