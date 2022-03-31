package gg.essential

import gg.essential.gradle.multiversion.apivalidation.ExtractApiFile
import gg.essential.gradle.multiversion.apivalidation.MergeApiFiles
import org.gradle.api.tasks.Sync

plugins {
    id("org.jetbrains.kotlinx.binary-compatibility-validator")
}

val mergedApiFile = file("api/$name.api")

val apiDump by tasks.registering(MergeApiFiles::class) {
    output.set(mergedApiFile)
    evaluationDependsOnChildren()
    inputs.from(subprojects.map { project ->
        project.tasks.named("apiDump").map { project.file("api/${project.name}.api") }
    })
}

subprojects {
    val projectApiDir = project.file("api").also { it.mkdirs() }
    val extractApiDefinition by tasks.registering(ExtractApiFile::class) {
        selector.set(project.name)
        input.set(mergedApiFile)
        output.set(projectApiDir.resolve("${project.name}.api"))
    }
    afterEvaluate {
        tasks.named("apiCheck") {
            dependsOn(extractApiDefinition)
        }
        tasks.named<Sync>("apiDump") {
            finalizedBy(apiDump)
        }
    }
}
