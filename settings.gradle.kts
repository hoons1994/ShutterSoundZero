pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            content {
                // libadb와 같은 공급자의 JitPack 전이 의존성까지 허용하되,
                // 다른 GitHub 그룹이 이 저장소에서 해석되지 않도록 범위를 제한한다.
                includeGroupByRegex("com\\.github\\.MuntashirAkon(\\..*)?")
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "ShutterSoundZero"
include(":app")
