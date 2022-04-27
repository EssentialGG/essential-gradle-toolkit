package gg.essential.gradle.multiversion

import org.gradle.api.GradleException
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

/**
 * Removes Kotlin's `$DefaultImpls` classes (and any references to them) from the given jar file as if the Kotlin code
 * was compiled with `-Xjvm-default=all`.
 *
 * This is useful if you have a platform-independent "common" project containing the vast majority of your code but, to
 * maintain backwards compatibility, you need to compile with `-Xjvm-default=all-compatibility` for some platforms.
 * Ordinarily this would then leak the DefaultImpls to all platforms and you'll be stuck with `all-compatibility` for
 * all of them (even when that would not have been required for modern versions).
 *
 * For such cases, this method allows you to strip the `$DefaultImpls` classes from a given platform-specific jar file:
 * ```kotlin
 * tasks.jar {
 *     if (platform.mcVersion >= 11400) {
 *         excludeKotlinDefaultImpls()
 *     }
 * }
 * ```
 */

fun AbstractArchiveTask.excludeKotlinDefaultImpls() {
    doLast { excludeKotlinDefaultImpls(archiveFile.get().asFile.toPath()) }
}

/**
 * See [excludeKotlinDefaultImpls].
 *
 * Modifies the given jar file in place.
 */
fun excludeKotlinDefaultImpls(jarPath: Path) {
    FileSystems.newFileSystem(jarPath).use { fileSystem ->
        val defaultImplsFiles = Files.walk(fileSystem.getPath("/")).use { stream ->
            stream.filter { it.fileName?.toString()?.endsWith("\$DefaultImpls.class") == true }.toList()
        }
        for (defaultImplsFile in defaultImplsFiles) {
            val baseFile = defaultImplsFile.resolveSibling(defaultImplsFile.fileName.toString().replace("\$DefaultImpls", ""))
            if (Files.notExists(baseFile)) {
                throw GradleException("Found $defaultImplsFile but no matching base class $baseFile.")
            }
            Files.write(baseFile, removeReferences(Files.readAllBytes(baseFile)))
            Files.delete(defaultImplsFile)
        }
    }
}

private fun removeReferences(bytes: ByteArray): ByteArray {
    val node = ClassNode().apply { ClassReader(bytes).accept(this, 0) }

    node.innerClasses.removeIf { it.name.endsWith("\$DefaultImpls") }

    return ClassWriter(0).apply { node.accept(this) }.toByteArray()
}
