package gg.essential

import gg.essential.gradle.multiversion.Platform
import gg.essential.gradle.util.RelocationTransform.Companion.registerRelocationAttribute


plugins {
    id("gg.essential.loom")
}

val platform = Platform.of(project)

val essentialLoader: Configuration by configurations.creating

when {
    platform.isLegacyForge -> {
        dependencies {
            "implementation"(essentialLoader("gg.essential:loader-launchwrapper:1.2.2")!!)
        }
        tasks.named<Jar>("jar") {
            dependsOn(essentialLoader)
            manifest.attributes(mapOf(
                "TweakClass" to "gg.essential.loader.stage0.EssentialSetupTweaker"
            ))
            from({ zipTree(essentialLoader.singleFile) })
        }
    }

    platform.isFabric -> {
        dependencies {
            "include"("modRuntimeOnly"("gg.essential:loader-fabric:1.2.2")!!)
        }
    }

    //FIXME: Fix loader not working on ml
    platform.isModLauncher -> {
        val isML8 = platform.mcVersion < 11700
        val relocatedPackage = findProperty("essential.loader.package")?.toString() ?: throw GradleException("""
                A package for the Essential loader to be relocated to has not been set.
                You need to set `essential.loader.package` in the project's `gradle.properties` file to a package where Essential's loader will be relocated to.
                For example: `essential.loader.package = org.example.coolmod.relocated.essential`
            """.trimIndent())
        val generatedResourcesDirectory = layout.buildDirectory.dir(findProperty("essential.loader.generatedResourcesDir")?.toString() ?: "essential-generated-resources")
        dependencies {
            val relocationAttribute =
                registerRelocationAttribute("essential-loader-relocated") {
                    relocate("gg.essential.loader.stage0", "$relocatedPackage.stage0")
                    // preserve stage1 path
                    rename("gg/essential/loader/stage0/stage1.jar", "gg/essential/loader/stage0/stage1.jar")
                }
            essentialLoader.attributes {
                attribute(relocationAttribute, true)
            }
            if (isML8) {
                "forgeRuntimeLibrary"(essentialLoader("gg.essential:loader-modlauncher8:1.2.2")!!)
            } else {
                "forgeRuntimeLibrary"(essentialLoader("gg.essential:loader-modlauncher9:1.2.2")!!)
            }
        }

        if (!isML8) {
            afterEvaluate {
                tasks.named<Jar>("jar") {
                    val mixinConfigs = manifest.attributes.getOrDefault("MixinConfigs", "") as String
                    manifest.attributes["MixinConfigs"] =
                        if (mixinConfigs.isBlank()) {
                            "mixin.stage0.essential-loader.json"
                        } else {
                            "$mixinConfigs,mixin.stage0.essential-loader.json"
                        }
                }
            }
        }

        tasks {
            named<Jar>("jar") {
                dependsOn(essentialLoader)
                from({ zipTree(essentialLoader.singleFile) }) {
                    if (!isML8) {
                        exclude("**/META-INF/services/**")
                    }
                }
            }
            register("generateEssentialLoaderMixinConfig") {
                val outputFile = file(generatedResourcesDirectory.get().file("mixin.stage0.essential-loader.json"))
                outputs.file(outputFile)
                doLast {
                    outputFile.writeText("""
                        {
                          "minVersion": "0.8",
                          "compatibilityLevel": "JAVA_16",
                          "plugin": "$relocatedPackage.stage0.EssentialStage0MixinPlugin",
                          "package" : "$relocatedPackage.stage0.dummy"
                        }
                    """.trimIndent())
                }
            }
            register("generateModNameMarker") {
                val modName = findProperty("essential.loader.modName")?.toString() ?: throw GradleException("""
                        A mod name has not been set.
                        You need to set `essential.loader.modName` in the project's `gradle.properties` file to the name of your mod.
                        For example: `essential.loader.modName=Cool Mod`
                    """.trimIndent())
                val outputFile = file(generatedResourcesDirectory.get().file("essential-loader-mod-name.txt"))
                outputs.file(outputFile)
                doLast {
                    outputFile.writeText(modName)
                }
            }
            named<ProcessResources>("processResources") {
                if (!isML8) {
                    dependsOn(named("generateEssentialLoaderMixinConfig"))
                    from(file(generatedResourcesDirectory.get().file("mixin.stage0.essential-loader.json")))
                    dependsOn(named("generateModNameMarker"))
                    from(file(generatedResourcesDirectory.get().file("essential-loader-mod-name.txt"))) {
                        into("META-INF")
                    }
                }
            }
        }
    }

    else -> error("No loader available for this platform")
}

// Unit statement so it doesn't try to get the result from the when block
Unit