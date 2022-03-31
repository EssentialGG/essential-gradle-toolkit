package gg.essential.gradle.multiversion.apivalidation

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class MergeApiFiles : DefaultTask() {
    @get:InputFiles
    val inputs: ConfigurableFileCollection = project.files()

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun merge() {
        val parser = Parser(emptySet())
        val inputs = inputs.files.sortedBy { it.name }.associate { file ->
            file.name.removeSuffix(".api") to parser.parseFile(file.readText())
        }

        val output = ApiFile(mutableListOf())
        for (input in inputs) {
            input.value.mergeInto(output, input.key)
        }

        val outputStr = Writer(project.childProjects.keys).write(output)
        this.output.get().asFile.writeText(outputStr)
    }
}
