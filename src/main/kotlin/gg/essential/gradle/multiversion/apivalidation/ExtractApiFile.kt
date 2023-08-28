package gg.essential.gradle.multiversion.apivalidation

import gg.essential.gradle.util.multiversionChildProjects
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class ExtractApiFile : DefaultTask() {
    @get:InputFile
    abstract val input: RegularFileProperty

    @get:Input
    abstract val selector: Property<String>

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun merge() {
        val parser = Parser(project.parent!!.multiversionChildProjects.keys)
        val input = parser.parseFile(input.get().asFile.readText())
        val outputStr = Writer(emptySet()).write(input.filtered(selector.get()))
        this.output.get().asFile.writeText(outputStr)
    }
}
