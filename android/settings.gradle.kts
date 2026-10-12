pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") {
            content {
                excludeGroup("io.github.tribalfs")
                excludeGroupByRegex("sesl\\..*")
            }
        }
        // Samsung One UI widgets (SESL), published by tribalfs on GitHub Packages. The read:packages
        // token comes from gpr.user / gpr.key in the user's ~/.gradle/gradle.properties.
        for (repo in listOf("oneui-design", "sesl-androidx", "sesl-material-components-android")) {
            maven("https://maven.pkg.github.com/tribalfs/$repo") {
                credentials {
                    username = providers.gradleProperty("gpr.user").orElse(providers.environmentVariable("GPR_USER")).orNull
                    password = providers.gradleProperty("gpr.key").orElse(providers.environmentVariable("GPR_KEY")).orNull
                }
                content {
                    when (repo) {
                        "oneui-design" -> includeGroup("io.github.tribalfs")
                        "sesl-androidx" -> includeGroupByRegex("sesl\\.androidx\\..*")
                        "sesl-material-components-android" -> includeGroup("sesl.com.google.android.material")
                    }
                }
            }
        }
    }
}

rootProject.name = "LibrePods"
include(":app")
