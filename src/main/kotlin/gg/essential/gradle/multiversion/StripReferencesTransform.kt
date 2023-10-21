package gg.essential.gradle.multiversion

import gg.essential.gradle.util.compatibleKotlinMetadataVersion
import kotlinx.metadata.KmClassifier
import kotlinx.metadata.KmType
import kotlinx.metadata.KmValueParameter
import kotlinx.metadata.jvm.KotlinClassMetadata
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
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import java.io.Closeable
import java.io.File
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

/**
 * Strips all references to classes in the given package(s) from the artifact to which it is applied.
 *
 * This is useful if you wish to set up a platform-independent "common" project that depends on other mods with mostly
 * platform-independent API (such as Vigilance, Elementa, and UniversalCraft).
 * If you simply add a direct dependency on such a mod, the compiler may error if it sees a class that's not actually
 * on the classpath (such as one of the Minecraft classes), even if it doesn't appear to be necessary for your code.
 * This transform allows bypassing such errors by stripping any such references from the bytecode, leaving the compiler
 * properly in the dark.
 *
 * Do note that this is not a fool-proof way to prevent usage of platform-specific APIs from your "common" project. An
 * API may be specific to a given platform without directly depending on any Minecraft class. If that is your goal, you
 * will have to implement a more complex solution that merges all variants of your dependency into a single one, keeping
 * only what is present in all of them.
 *
 * To simplify setup, use [registerStripReferencesAttribute].
 */
abstract class StripReferencesTransform : TransformAction<StripReferencesTransform.Parameters> {
    interface Parameters : TransformParameters {
        @get:Input
        val excludes: SetProperty<String>
    }

    @get:InputArtifact
    abstract val input: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val excludes = parameters.excludes.get()
            .map { it.replace('.', '/') }
            .toSet()

        val input = input.get().asFile
        val output = outputs.file(input.nameWithoutExtension + "-common.jar")
        (input to output).useInOut { jarIn, jarOut ->
            while (true) {
                val entry = jarIn.nextJarEntry ?: break
                val originalBytes = jarIn.readBytes()

                val modifiedBytes = if (entry.name.endsWith(".class")) {
                    val reader = ClassReader(originalBytes)
                    val writer = ClassWriter(reader, 0)
                    reader.accept(ClassTransformer(writer, excludes), 0)
                    writer.toByteArray()
                } else {
                    originalBytes
                }

                jarOut.putNextEntry(ZipEntry(entry.name))
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

    private class ClassTransformer(
        inner: ClassVisitor,
        private val excludes: Set<String>,
    ) : ClassVisitor(Opcodes.ASM9, inner) {

        private fun excluded(name: String) = excludes.any { name.startsWith(it) }

        private fun excluded(type: Type): Boolean = when (type.sort) {
            Type.OBJECT, Type.ARRAY -> excluded(type.internalName)
            Type.METHOD -> excluded(type.returnType) || type.argumentTypes.any { excluded(it) }
            else -> false
        }

        private fun excluded(type: KmType?) = type != null && excluded(type.classifier)

        private fun excluded(classifier: KmClassifier) = when (classifier) {
            is KmClassifier.Class -> excluded(classifier.name)
            else -> false
        }

        private fun excluded(params: List<KmValueParameter>) = params.any { excluded(it.type) }

        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor {
            return if (descriptor == "Lkotlin/Metadata;") {
                KotlinMetadataTransformer(super.visitAnnotation(descriptor, visible), descriptor)
            } else  {
                super.visitAnnotation(descriptor, visible)
            }
        }

        override fun visit(
            version: Int,
            access: Int,
            name: String,
            signature: String?,
            superName: String?,
            interfaces: Array<out String>?
        ) {
            super.visit(
                version,
                access,
                name,
                signature,
                superName?.takeUnless { excluded(it) },
                interfaces?.filterNot { excluded(it) }?.toTypedArray(),
            )
        }

        override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            exceptions: Array<out String>?
        ): MethodVisitor? {
            return if (excluded(Type.getMethodType(descriptor)) || exceptions?.any { excluded(it) } == true) {
                null
            } else {
                super.visitMethod(access, name, descriptor, signature, exceptions)
            }
        }

        override fun visitField(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            value: Any?
        ): FieldVisitor? {
            return if (excluded(Type.getType(descriptor))) {
                null
            } else {
                super.visitField(access, name, descriptor, signature, value)
            }
        }

        inner class KotlinMetadataTransformer(val inner: AnnotationVisitor, desc: String) : AnnotationNode(Opcodes.ASM9, desc) {
            override fun visitEnd() {
                var metadata = kotlinMetadata ?: return

                val extraInt = metadata.annotationData.extraInt
                val metadataVersion = compatibleKotlinMetadataVersion(metadata.annotationData.metadataVersion)

                when (metadata) {
                    is KotlinClassMetadata.Class -> {
                        val cls = metadata.toKmClass()
                        cls.supertypes.removeIf { excluded(it.classifier) }
                        cls.properties.removeIf { excluded(it.returnType) || excluded(it.receiverParameterType) }
                        cls.functions.removeIf { excluded(it.returnType) || excluded(it.receiverParameterType) || excluded(it.valueParameters) }
                        metadata = KotlinClassMetadata.writeClass(cls, metadataVersion, extraInt)
                    }
                    else -> {}
                }

                kotlinMetadata = metadata

                accept(inner)
            }
        }
    }

    companion object {
        @JvmStatic
        fun Project.registerStripReferencesAttribute(name: String, configure: Parameters.() -> Unit): Attribute<Boolean> {
            val attribute = Attribute.of(name, Boolean::class.javaObjectType)

            dependencies.registerTransform(StripReferencesTransform::class.java) {
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
