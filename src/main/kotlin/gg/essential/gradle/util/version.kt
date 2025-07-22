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

private fun Project.branch(): String = project.properties["branch"]?.toString() ?:
    providers.exec {
        commandLine("git", "rev-parse", "--abbrev-ref", "HEAD")
    }.let { execOutput ->
        execOutput.result.flatMap { result ->
            if (result.exitValue == 0) {
                execOutput.standardOutput.asText.map { it.trim() }
            } else {
                provider { "unknown" }
            }
        }
    }
        // FIXME should ideally not read this here because it makes the configuration cache less effective
        //  but `version` doesn't yet support `Provider`: https://github.com/gradle/gradle/issues/13672
        .get()
