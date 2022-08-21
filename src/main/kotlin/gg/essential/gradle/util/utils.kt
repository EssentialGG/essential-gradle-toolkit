package gg.essential.gradle.util

import gg.essential.gradle.multiversion.Platform
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.fabricmc.loom.bootstrap.LoomGradlePluginBootstrap
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.extra

internal fun checkJavaVersion(minVersion: JavaVersion) {
    if (JavaVersion.current() < minVersion) {
        throw GradleException(listOf(
            "Java $minVersion is required to build (running ${JavaVersion.current()}).",
            if (System.getProperty("idea.active").toBoolean()) {
                "In IDEA: Settings -> Build, Execution, Deployment -> Build Tools -> Gradle -> Gradle JVM"
            } else {
                "Current JAVA_HOME: ${System.getenv("JAVA_HOME")}"
            },
        ).joinToString("\n"))
    }
}

internal fun compatibleKotlinMetadataVersion(version: IntArray): IntArray {
    // Upgrade versions older than 1.4 to 1.4 in accordance with https://youtrack.jetbrains.com/issue/KT-41011
    if (version.size < 2 || version[0] < 1 || version[0] <= 1 && version[1] < 4) {
        return intArrayOf(1, 4)
    }
    return version
}

fun Project.setupLoomPlugin(platform: Platform, block: LoomGradleExtensionAPI.(platform: Platform) -> Unit) {
    extra.set("loom.platform", if (platform.isForge) "forge" else "fabric")

    apply<LoomGradlePluginBootstrap>()

    extensions.configure<LoomGradleExtensionAPI> {
        block(platform)
    }
}