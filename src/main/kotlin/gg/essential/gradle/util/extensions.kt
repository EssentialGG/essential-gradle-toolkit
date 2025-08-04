package gg.essential.gradle.util

import net.fabricmc.loom.api.LoomGradleExtensionAPI
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Sets `-Xjvm-default=mode` on the given task.
 */
fun KotlinCompile.setJvmDefault(mode: String) {
    compilerOptions {
        val key = "-Xjvm-default="
        freeCompilerArgs = freeCompilerArgs.get().filterNot { it.startsWith(key) } + listOf(key + mode)
    }
}

/**
 * Sets `-Xjvm-default=mode` on the given task.
 */
fun TaskProvider<KotlinCompile>.setJvmDefault(mode: String) = configure { setJvmDefault(mode) }

/**
 * Disables all run configs.
 */
fun LoomGradleExtensionAPI.noRunConfigs() {
    runConfigs.all { isIdeConfigGenerated = false }
}

/**
 * Disables the server run config.
 */
fun LoomGradleExtensionAPI.noServerRunConfigs() {
    runConfigs["server"].isIdeConfigGenerated = false
}
