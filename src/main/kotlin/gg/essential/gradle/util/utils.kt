package gg.essential.gradle.util

import kotlinx.metadata.jvm.JvmMetadataVersion
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import java.util.Calendar
import java.util.GregorianCalendar

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

internal fun compatibleKotlinMetadataVersion(version: IntArray): JvmMetadataVersion {
    // Upgrade versions older than 1.4 to 1.4 in accordance with https://youtrack.jetbrains.com/issue/KT-41011
    if (version.size < 2 || version[0] < 1 || version[0] <= 1 && version[1] < 4) {
        return JvmMetadataVersion(1, 4)
    }
    return JvmMetadataVersion(version[0], version[1], version[2])
}

// A safe, constant value for creating consistent zip entries
// From: https://github.com/gradle/gradle/blob/d6c7fd470449a59fc57a26b4ebc0ad83c64af50a/subprojects/core/src/main/java/org/gradle/api/internal/file/archive/ZipCopyAction.java#L42-L57
val CONSTANT_TIME_FOR_ZIP_ENTRIES = GregorianCalendar(1980, Calendar.FEBRUARY, 1, 0, 0, 0).timeInMillis