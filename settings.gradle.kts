

pluginManagement {
    repositories {
        maven {
            // RetroFuturaGradle
            name = "GTNH Maven"
            url = uri("https://nexus.gtnewhorizons.com/repository/public/")
            mavenContent {
                includeGroup("com.gtnewhorizons")
                includeGroupByRegex("com\\.gtnewhorizons\\..+")
            }
        }
        gradlePluginPortal()
        maven {
            name = "Aliyun Maven Public"
            url = uri("https://maven.aliyun.com/repository/public/")
        }
        mavenCentral()
        mavenLocal()
        maven {
            name = "JitPack"
            url = uri("https://jitpack.io")
        }
    }
}

dependencyResolutionManagement {
    repositories {
        maven {
            name = "Aliyun Maven Public"
            url = uri("https://maven.aliyun.com/repository/public/")
        }
    }
}

plugins {
    id("com.gtnewhorizons.gtnhsettingsconvention") version("2.0.20")
}


