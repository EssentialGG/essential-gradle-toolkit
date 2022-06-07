package gg.essential.gradle.util

import org.gradle.api.GradleException
import org.gradle.api.JavaVersion

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
