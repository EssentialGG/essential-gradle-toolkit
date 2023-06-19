package gg.essential.gradle.multiversion

import gg.essential.gradle.util.compatibleKotlinMetadataVersion
import kotlinx.metadata.KmClass
import kotlinx.metadata.KmDeclarationContainer
import kotlinx.metadata.KmPackage
import kotlinx.metadata.jvm.KotlinClassMetadata
import kotlinx.metadata.jvm.Metadata
import org.gradle.api.GradleException
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.objectweb.asm.*
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.SimpleRemapper
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

/**
 * Merges classes with a `_platform` suffix into the corresponding class files without the suffix and deletes the
 * suffixed files.
 *
 * This is useful if you have a platform-independent "common" project containing the vast majority of your code but also
 * a small amount of API methods which depend on platform-specific types where removing them would constitute a breaking
 * change.
 * For such cases, this method allows you to define a class with the same name (suffixed with `_platform`) in your
 * platform-specific projects and declare the API methods in there.
 * Then, after the common classes have been combined into a single jar file with the platform-specific code, this method
 * can be called on the jar file to merge the platform-specific classes into the common ones.
 * ```kotlin
 * tasks.jar {
 *     mergePlatformSpecifics()
 * }
 * ```
 */

fun AbstractArchiveTask.mergePlatformSpecifics() {
    doLast { mergePlatformSpecifics(archiveFile.get().asFile.toPath()) }
}

/**
 * See [mergePlatformSpecifics].
 *
 * Modifies the given jar file in place.
 */
fun mergePlatformSpecifics(jarPath: Path) {
    FileSystems.newFileSystem(jarPath).use { fileSystem ->
        val platformFiles = Files.walk(fileSystem.getPath("/")).use { stream ->
            stream.filter { it.fileName?.toString()?.endsWith("_platform.class") == true }.toList()
        }
        for (platformFile in platformFiles) {
            val targetFile = platformFile.resolveSibling(platformFile.fileName.toString().replace("_platform", ""))
            if (Files.notExists(targetFile)) {
                throw GradleException("Found platform-specific $platformFile but no matching target class $targetFile.")
            }
            Files.write(targetFile, merge(Files.readAllBytes(targetFile), Files.readAllBytes(platformFile)))
            Files.delete(platformFile)
        }
    }
}

private fun merge(targetBytes: ByteArray, platformBytes: ByteArray): ByteArray {
    val targetNode = ClassNode().apply { ClassReader(targetBytes).accept(this, 0) }
    val platformNode = ClassNode().apply { ClassReader(platformBytes).accept(this, 0) }

    try {
        merge(targetNode, platformNode)
    } catch (e: Exception) {
        throw GradleException("Failed to merge ${platformNode.name} into ${targetNode.name}", e)
    }

    return ClassWriter(0).apply {
        // At this point, stuff from the platform class has been merged into the target class, but the merged code
        // may still refer to the platform class if the platform code calls its own methods.
        // To remedy that, we simply remap the platform class name to match the target class name.
        val remapper = SimpleRemapper(platformNode.name, targetNode.name)
        targetNode.accept(ClassRemapper(this, remapper))
    }.toByteArray()
}

private fun merge(targetClass: ClassNode, sourceClass: ClassNode) {
    for (field in sourceClass.fields) {
        if (targetClass.fields.any { it.name == field.name && it.desc == field.desc }) {
            throw UnsupportedOperationException("Field ${field.name}:${field.desc} already present in ${targetClass.name}")
        }

        targetClass.fields.add(field)
    }

    for (method in sourceClass.methods) {
        if (method.name == "<init>") {
            continue
        }

        if (targetClass.methods.any { it.name == method.name && it.desc == method.desc }) {
            throw UnsupportedOperationException("Method ${method.name}${method.desc} already present in ${targetClass.name}")
        }

        targetClass.methods.add(method)
    }

    val targetMetadata = targetClass.kotlinMetadata ?: return
    val sourceMetadata = sourceClass.kotlinMetadata ?: return

    val sourceHeader = sourceMetadata.annotationData
    val extraInt = sourceHeader.extraInt
    val metadataVersion = compatibleKotlinMetadataVersion(sourceHeader.metadataVersion)

    val mergedMetadata = when {
        sourceMetadata is KotlinClassMetadata.Class && targetMetadata is KotlinClassMetadata.Class -> {
            val targetKmClass = targetMetadata.toKmClass()
            val sourceKmClass = sourceMetadata.toKmClass()
            merge(targetKmClass, sourceKmClass)
            KotlinClassMetadata.writeClass(targetKmClass, metadataVersion, extraInt)
        }
        sourceMetadata is KotlinClassMetadata.FileFacade && targetMetadata is KotlinClassMetadata.FileFacade -> {
            val targetKmPackage = targetMetadata.toKmPackage()
            val sourceKmPackage = sourceMetadata.toKmPackage()
            merge(targetKmPackage, sourceKmPackage)
            KotlinClassMetadata.writeFileFacade(targetKmPackage, metadataVersion, extraInt)
        }
        else -> throw UnsupportedOperationException("Don't know how to merge ${sourceMetadata.javaClass} into ${targetMetadata.javaClass}")
    }

    targetClass.kotlinMetadata = mergedMetadata
}

private fun merge(targetClass: KmClass, sourceClass: KmClass) {
    mergeDeclarationContainer(targetClass, sourceClass)
}

private fun merge(targetPackage: KmPackage, sourcePackage: KmPackage) {
    mergeDeclarationContainer(targetPackage, sourcePackage)
}

private fun mergeDeclarationContainer(targetContainer: KmDeclarationContainer, sourceContainer: KmDeclarationContainer) {
    for (property in sourceContainer.properties) {
        targetContainer.properties.add(property)
    }
    for (function in sourceContainer.functions) {
        targetContainer.functions.add(function)
    }
    for (typeAlias in sourceContainer.typeAliases) {
        targetContainer.typeAliases.add(typeAlias)
    }
}

private const val KotlinMetadata_Desc = "Lkotlin/Metadata;"

private var ClassNode.kotlinMetadata: KotlinClassMetadata?
    get() {
        val annotation = visibleAnnotations.find { it.desc == KotlinMetadata_Desc } ?: return null
        return annotation.kotlinMetadata
    }
    set(value) {
        visibleAnnotations.removeIf { it.desc == KotlinMetadata_Desc }

        val annotation = AnnotationNode(KotlinMetadata_Desc)
        annotation.kotlinMetadata = value ?: return
        visibleAnnotations.add(annotation)
    }

internal var AnnotationNode.kotlinMetadata: KotlinClassMetadata?
    get() {
        val values = values.windowed(2, 2).associate { (key, value) -> key to value }
        return KotlinClassMetadata.read(with(values) {
            @Suppress("UNCHECKED_CAST")
            Metadata(
                kind = get("k") as Int?,
                metadataVersion = (get("mv") as List<Int>?)?.toIntArray(),
                data1 = (get("d1") as List<String>?)?.toTypedArray(),
                data2 = (get("d2") as List<String>?)?.toTypedArray(),
                extraString = get("xs") as String?,
                packageName = get("pn") as String?,
                extraInt = get("xi") as Int?
            )
        })
    }
    set(value) {
        with((value ?: return).annotationData) {
            values = mapOf(
                "k" to kind,
                "mv" to metadataVersion.toList(),
                "d1" to data1.toList(),
                "d2" to data2.toList(),
                "xs" to extraString.takeIf { it != "" },
                "pn" to packageName.takeIf { it != "" },
                "xi" to extraInt,
            ).filterValues { it != null }.flatMap { listOf(it.key, it.value) }
        }
    }
