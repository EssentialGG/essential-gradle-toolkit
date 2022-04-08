package gg.essential

import com.replaymod.gradle.preprocess.PreprocessExtension
import com.replaymod.gradle.preprocess.PreprocessPlugin
import gg.essential.gradle.multiversion.Platform
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.fabricmc.loom.bootstrap.LoomGradlePluginBootstrap
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
}

val platform = Platform.of(project)

extensions.add("platform", platform)

setupLoomPlugin()
setupPreprocessPlugin()
configureJavaVersion()
afterEvaluate { configureResources() } // delayed because it needs project.version
parent?.let(::inheritConfigurationFrom)

fun setupLoomPlugin() {
    extra.set("loom.platform", if (platform.isForge) "forge" else "fabric")

    apply<LoomGradlePluginBootstrap>()

    extensions.configure<LoomGradleExtensionAPI> {
        runConfigs.all {
            isIdeConfigGenerated = true
        }
    }
}

fun setupPreprocessPlugin() {
    apply<PreprocessPlugin>()

    extensions.configure<PreprocessExtension> {
        vars.put("MC", mcVersion)
        vars.put("FABRIC", if (platform.isFabric) 1 else 0)
        vars.put("FORGE", if (platform.isForge) 1 else 0)
    }
}

fun configureJavaVersion() {
    configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(platform.javaVersion.majorVersion))
    }

    pluginManager.withPlugin("kotlin") {
        configure<KotlinJvmProjectExtension> {
            jvmToolchain {
                (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of(platform.javaVersion.majorVersion))
            }
        }

        tasks.withType<KotlinCompile> {
            kotlinOptions {
                // FIXME this should not be necessary because it is implied by the toolchain set above but IDEA seems to not
                //       recognize that and then errors when compiling
                jvmTarget = platform.javaVersion.toString()
            }
        }
    }
}

fun configureResources() {
    tasks.processResources {
        // We define certain Kotlin/Groovy-style expansions to be used in the platform-specific mod metadata files
        val expansions = mapOf(
            "version" to project.version,
            "mcVersionStr" to platform.mcVersionStr,
            // New forge needs the version to be exactly `${file.jarVersion}` to not explode in dev but that
            // also qualifies for replacement here, so we need to handle this ugly case.
            // And forge needs it to start with a number...
            "file" to mapOf("jarVersion" to project.version.toString().let { if (it[0].isDigit()) it else "0.$it" }),
        )

        // TODO is this required? are the FileCopyDetails not part of the input already?
        inputs.property("mod_version_expansions", expansions)

        filesMatching(listOf("mcmod.info", "META-INF/mods.toml", "fabric.mod.json")) {
            expand(expansions)
        }

        // And exclude mod metadata files for other platforms
        if (!platform.isFabric) exclude("fabric.mod.json")
        if (!platform.isModLauncher) exclude("META-INF/mods.toml")
        if (!platform.isLegacyForge) exclude("mcmod.info")
    }
}

fun inheritConfigurationFrom(parent: Project) {
    // Inherit version from parent
    if (version == Project.DEFAULT_VERSION) {
        version = parent.version
    }

    // Inherit base archives name from parent, suffixed with the project name
    val parentBase = parent.extensions.findByType<BasePluginExtension>()
    if (parentBase != null) {
        base.archivesName.convention(parentBase.archivesName.map { "$it ${project.name}" })
    }

    afterEvaluate {
        tasks.withType<KotlinCompile> {
            kotlinOptions {
                if ("-module-name" !in freeCompilerArgs) {
                    val moduleName = project.findProperty("baseArtifactId")?.toString()
                            ?: parentBase?.archivesName?.orNull
                            ?: parent.name.toLowerCase()
                    freeCompilerArgs = freeCompilerArgs + listOf("-module-name", moduleName)
                }
            }
        }
    }
}
