pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/tribalfs/sesl-androidx")
            credentials {
                username = System.getenv("GPR_USER") ?: System.getenv("GITHUB_ACTOR") ?: ""
                password = System.getenv("GPR_TOKEN") ?: System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
        maven {
            url = uri("https://maven.pkg.github.com/tribalfs/sesl-material-components-android")
            credentials {
                username = System.getenv("GPR_USER") ?: System.getenv("GITHUB_ACTOR") ?: ""
                password = System.getenv("GPR_TOKEN") ?: System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
    }
}

rootProject.name = "S23Enhancer"
include(":app")
