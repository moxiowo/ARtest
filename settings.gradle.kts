pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        flatDir {
            dirs("libs") // ⬅️ 放你自己的 AAR
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        flatDir {
            dirs("libs") // ⬅️ 放你自己的 AAR
        }
    }
}

rootProject.name = "專題AR"
include(":app")
