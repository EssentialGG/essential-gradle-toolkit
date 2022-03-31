package gg.essential.gradle.util

import org.gradle.api.Project
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import java.io.Closeable
import java.io.File
import java.io.Serializable
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

/**
 * Relocates packages and single files in an artifact.
 *
 * If a package is relocated, the folder containing it will be relocated as a whole.
 * File renames take priority over package relocations.
 *
 * The packages do not have to be part of the artifact, e.g. a completely valid use case would be relocating guava
 * packages in an artifact using guava (for actual use, you'd of course also have to relocate the guava artifact itself,
 * otherwise the classes referred to after the relocation will not exist). This can be used together with [prebundle] to
 * create fat jars which apply at dev time (to e.g. use two different versions of the same library).
 *
 * To simplify setup, use [registerRelocationAttribute].
 */
abstract class RelocationTransform : TransformAction<RelocationTransform.Parameters> {
    interface Parameters : TransformParameters {
        @get:Input
        val relocations: SetProperty<Relocation>

        @get:Input
        val renames: SetProperty<Rename>

        fun relocate(sourcePackage: String, targetPackage: String) =
            relocations.add(Relocation(sourcePackage, targetPackage))

        fun rename(sourceFile: String, targetFile: String) =
            renames.add(Rename(sourceFile, targetFile))
    }

    data class Relocation(val sourcePackage: String, val targetPackage: String) : Serializable
    data class Rename(val sourceFile: String, val targetFile: String) : Serializable

    @get:InputArtifact
    abstract val input: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val fileMap = parameters.renames.get().associate { it.sourceFile to it.targetFile }
        val packageMap = parameters.relocations.get().associate {
            it.sourcePackage.replace('.', '/') + '/' to it.targetPackage.replace('.', '/') + '/'
        }
        val remapper = object : Remapper() {
            override fun map(typeName: String): String {
                for ((sourcePackage, targetPackage) in packageMap) {
                    if (typeName.startsWith(sourcePackage)) {
                        return targetPackage + typeName.substring(sourcePackage.length)
                    }
                }
                return typeName
            }
        }

        val input = input.get().asFile
        val output = outputs.file(input.nameWithoutExtension + "-relocated.jar")
        (input to output).useInOut { jarIn, jarOut ->
            while (true) {
                val entry = jarIn.nextJarEntry ?: break
                val originalBytes = jarIn.readBytes()

                val modifiedBytes = if (entry.name.endsWith(".class")) {
                    val reader = ClassReader(originalBytes)
                    // Not copying the constant pool cause that leaves references to the old classes which, while any
                    // lazy tool will never end up resolving them, do get resolved by e.g. proguard.
                    val writer = ClassWriter(0)
                    reader.accept(ClassRemapper(writer, remapper), 0)
                    writer.toByteArray()
                } else {
                    originalBytes
                }

                jarOut.putNextEntry(ZipEntry(fileMap[entry.name] ?: remapper.map(entry.name)))
                jarOut.write(modifiedBytes)
                jarOut.closeEntry()
            }
        }
    }

    private inline fun Pair<File, File>.useInOut(block: (jarIn: JarInputStream, jarOut: JarOutputStream) -> Unit) =
        first.inputStream().nestedUse(::JarInputStream) { jarIn ->
            second.outputStream().nestedUse(::JarOutputStream) { jarOut ->
                block(jarIn, jarOut)
            }
        }

    private inline fun <T: Closeable, U: Closeable> T.nestedUse(nest: (T) -> U, block: (U) -> Unit) =
        use { nest(it).use(block) }

    companion object {
        fun Project.registerRelocationAttribute(name: String, configure: Parameters.() -> Unit): Attribute<Boolean> {
            val attribute = Attribute.of(name, Boolean::class.javaObjectType)

            dependencies.registerTransform(RelocationTransform::class.java) {
                from.attribute(attribute, false)
                to.attribute(attribute, true)
                parameters(configure)
            }

            dependencies.artifactTypes.all {
                attributes.attribute(attribute, false)
            }

            return attribute
        }
    }
}