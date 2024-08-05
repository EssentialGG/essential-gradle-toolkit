package gg.essential.defaults

import gg.essential.gradle.multiversion.Platform

plugins {
    id("java-library")
    id("maven-publish")
}

publishing {
    publications {
        register<MavenPublication>("maven") {
            from(components["java"])

            pluginManager.withPlugin("gg.essential.multi-version") {
                val platform: Platform by extensions
                val baseArtifactId = (if (parent == rootProject) rootProject.name.lowercase() else null)
                    ?: project.findProperty("baseArtifactId")?.toString()
                    ?: throw GradleException("No default base maven artifact id found. Set `baseArtifactId` in the `gradle.properties` file of the multi-version-root project.")
                artifactId = "$baseArtifactId-$platform"
            }
        }
    }

    repositories {
        val nexusUser = project.findProperty("nexus_user")
        val nexusPassword = project.findProperty("nexus_password")
        if (nexusUser != null && nexusPassword != null) {
            maven("https://repo.essential.gg/repository/maven-releases/") {
                name = "nexus-public"
                credentials {
                    username = nexusUser.toString()
                    password = nexusPassword.toString()
                }
            }
        }
    }
}
