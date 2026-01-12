package gg.essential

import gg.essential.gradle.multiversion.apivalidation.ExtractApiFile
import gg.essential.gradle.multiversion.apivalidation.MergeApiFiles
import gg.essential.gradle.util.isMultiversionChildProject
import gg.essential.gradle.util.multiversionChildProjects

plugins {
    id("org.jetbrains.kotlinx.binary-compatibility-validator")
}

val mergedApiFile = file("api/$name.api")

val apiDump by tasks.registering(MergeApiFiles::class) {
    output.set(mergedApiFile)
    evaluationDependsOnChildren()
    inputs.from(multiversionChildProjects.map { (_, project) ->
        project.tasks.named("apiDump").map { project.file("api/${project.name}.api") }
    })
}

subprojects {
    pluginManager.withPlugin("gg.essential.multi-version") {
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
            tasks.named("apiDump") {
                finalizedBy(apiDump)
            }
        }

        // Bump ASM dependencies for Java 25 compatibility
        dependencies {
            `bcv-rt-jvm-cp`("org.ow2.asm:asm:9.9.1")
            `bcv-rt-jvm-cp`("org.ow2.asm:asm-tree:9.9.1")
        }
    }
}
