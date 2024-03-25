plugins {
    `kotlin-dsl`
    `maven-publish`
}

group = "gg.essential"
version = "0.4.0"

java.withSourcesJar()

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven(url = "https://maven.fabricmc.net/")
    maven(url = "https://maven.minecraftforge.net")
    maven(url = "https://jitpack.io")
    maven(url = "https://maven.architectury.dev/")
    maven(url = "https://repo.essential.gg/repository/maven-public")
}

dependencies {
    implementation(gradleApi())
    implementation(localGroovy())

    api(libs.archloom)
    implementation(libs.archloomPack200)

    compileOnly(libs.kotlin.gradlePlugin)
    implementation(libs.kotlinx.binaryCompatibilityValidator)
    implementation(libs.proguard) {
        exclude(group = "org.jetbrains.kotlin")
    }
    implementation("gradle.plugin.com.github.jengelman.gradle.plugins:shadow:7.0.0")
    api(libs.preprocessor)
    implementation(libs.asm)
    implementation(libs.guava)
    implementation(libs.kotlinx.metadata.jvm)
}

publishing {
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
