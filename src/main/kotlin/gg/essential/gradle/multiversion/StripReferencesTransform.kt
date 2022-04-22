package gg.essential.gradle.multiversion

import kotlinx.metadata.KmClassifier
import kotlinx.metadata.KmType
import kotlinx.metadata.KmValueParameter
import kotlinx.metadata.jvm.KotlinClassHeader
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
import java.io.Closeable
import java.io.File
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
                KotlinMetadataTransformer(super.visitAnnotation(descriptor, visible))
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

        inner class KotlinMetadataTransformer(inner: AnnotationVisitor) : AnnotationVisitor(Opcodes.ASM9, inner) {
            private var kind: Int? = null
            private var metadataVersion: IntArray? = null
            private var data1: Array<String>? = null
            private var data2: Array<String>? = null
            private var extraString: String? = null
            private var packageName: String? = null
            private var extraInt: Int? = null

            override fun visit(name: String, value: Any?) = when(name) {
                "k" -> kind = value as Int?
                "mv" -> metadataVersion = value as IntArray?
                "xs" -> extraString = value as String?
                "pn" -> packageName = value as String?
                "xi" -> extraInt = value as Int?
                else -> {}
            }

            override fun visitArray(name: String?): AnnotationVisitor? = when (name) {
                "d1" -> StringArrayVisitor { data1 = it }
                "d2" -> StringArrayVisitor { data2 = it }
                else -> null
            }

            override fun visitEnd() {
                val header = KotlinClassHeader(kind, metadataVersion, data1, data2, extraString, packageName, extraInt)
                var metadata = KotlinClassMetadata.read(header) ?: return

                when (metadata) {
                    is KotlinClassMetadata.Class -> {
                        val cls = metadata.toKmClass()
                        cls.supertypes.removeIf { excluded(it.classifier) }
                        cls.properties.removeIf { excluded(it.returnType) || excluded(it.receiverParameterType) }
                        cls.functions.removeIf { excluded(it.returnType) || excluded(it.receiverParameterType) || excluded(it.valueParameters) }
                        metadata = KotlinClassMetadata.Class.Writer().apply(cls::accept).write()
                    }
                    else -> {}
                }

                with(metadata.header) {
                    super.visit("k", kind)
                    super.visit("mv", metadataVersion)
                    super.visit("xs", extraString)
                    super.visit("pn", packageName)
                    super.visit("xi", extraInt)
                    super.visitArray("d1").let {
                        for (s in data1) {
                            it.visit("", s)
                        }
                        it.visitEnd()
                    }
                    super.visitArray("d2").let {
                        for (s in data2) {
                            it.visit("", s)
                        }
                        it.visitEnd()
                    }
                }

                super.visitEnd()
            }
        }
    }

    class StringArrayVisitor(private val result: (Array<String>) -> Unit) : AnnotationVisitor(Opcodes.ASM9) {
        private val list = mutableListOf<String>()
        override fun visit(name: String?, value: Any?) {
            list.add(value.toString())
        }

        override fun visitEnd() {
            result(list.toTypedArray())
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
