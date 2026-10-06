pluginManagement {
    repositories {
        gradlePluginPortal {
            content { excludeGroup("org.apache.logging.log4j") }
        }
        mavenCentral()
        // Unimined (Cleanroom's maintained fork) and its Forge/Fabric tooling
        maven("https://maven.arcseekers.com/releases")
        maven("https://maven.wagyourtail.xyz/releases")
        maven("https://maven.wagyourtail.xyz/snapshots")
        maven("https://maven.minecraftforge.net/")
        maven("https://maven.fabricmc.net/")
    }
}

plugins {
    // Provisions JDK 25 for the toolchain when the machine has none
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "impetus"

include("common")
