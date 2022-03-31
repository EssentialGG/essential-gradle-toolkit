package gg.essential.gradle.util

import org.gradle.api.Project

fun Project.versionFromBuildIdAndBranch(): String {
    val branch = branch().replace('/', '-')
    var version = buildId() ?: return "$branch-SNAPSHOT"
    if (branch != "master") {
        version += "+$branch"
    }
    return version
}

private fun Project.buildId(): String? = project.properties["BUILD_ID"]?.toString()

private fun Project.branch(): String = project.properties["branch"]?.toString() ?: try {
    val stdout = java.io.ByteArrayOutputStream()
    exec {
        commandLine("git", "rev-parse", "--abbrev-ref", "HEAD")
        standardOutput = stdout
    }
    stdout.toString().trim()
} catch (e: Throwable) {
    "unknown"
}
