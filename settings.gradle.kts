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
        @Suppress("ktlint:standard:property-naming")
        val WholphinExtensionsUsername: String? =
            providers
                .gradleProperty("WholphinExtensionsUsername")
                .orElse(providers.environmentVariable("WHOLPHIN_EXTENSIONS_USERNAME"))
                .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                .orNull
        @Suppress("ktlint:standard:property-naming")
        val WholphinExtensionsPassword: String? =
            providers
                .gradleProperty("WholphinExtensionsPassword")
                .orElse(providers.environmentVariable("WHOLPHIN_EXTENSIONS_PASSWORD"))
                .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                .orNull
        if (!WholphinExtensionsUsername.isNullOrBlank() && !WholphinExtensionsPassword.isNullOrBlank()) {
            maven("https://maven.pkg.github.com/damontecres/wholphin-extensions") {
                name = "WholphinExtensions"
                credentials {
                    username = WholphinExtensionsUsername
                    password = WholphinExtensionsPassword
                }
            }
        }
    }
}

rootProject.name = "Ndorfin"
include(":app")
