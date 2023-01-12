package gg.essential.defaults

import dev.architectury.pack200.java.Pack200Adapter
import gg.essential.gradle.multiversion.Platform

plugins {
    id("gg.essential.loom")
}

val platform = Platform.of(project)

data class Revision(
    val yarn: Map<Int, String>,
    val mcp: Map<Int, String>,
    val fabricLoader: String,
    val forge: Map<Int, String>,
)
val revisions = mutableListOf<Revision>()

revisions.add(Revision(
    yarn = mapOf(
        // Versions available here: https://maven.fabricmc.net/net/fabricmc/yarn/
        11903 to "1.19.3+build.5:v2"
        11902 to "1.19.2+build.28:v2",
        11901 to "1.19.1+build.6:v2",
        11900 to "1.19+build.4:v2",
        11802 to "1.18.2+build.4:v2",
        11801 to "1.18.1+build.22:v2",
        11701 to "1.17.1+build.65:v2",
        11700 to "1.17+build.13:v2",
        11604 to "1.16.4+build.9:v2",
        11602 to "1.16.2+build.47:v2",
        11601 to "1.16.1+build.21:v2",
        11502 to "1.15.2+build.17",
        11404 to "1.14.4+build.18",
    ),
    mcp = mapOf(
        11602 to "snapshot:20201028-1.16.3",
        11502 to "stable:60-1.15",
        11404 to "stable:58-1.14.4",
        11202 to "stable:39-1.12",
        11201 to "stable:39-1.12",
        11200 to "stable:39-1.12",
        11102 to "stable:32-1.11",
        11100 to "stable:32-1.11",
        11002 to "stable:29-1.10.2",
        10904 to "stable:26-1.9.4",
        10809 to "stable:22-1.8.9",
        10800 to "stable:18-1.8",
        10710 to "stable:12-1.7.10",
    ),
    fabricLoader = "0.14.2",
    forge = mapOf(
        11903 to "1.19.3-44.1.0"
        11902 to "1.19.2-43.2.0",
        11900 to "1.19-41.1.0",
        11801 to "1.18.1-39.1.0",
        11701 to "1.17.1-37.1.1",
        11602 to "1.16.2-33.0.61",
        11502 to "1.15.2-31.2.57",
        11404 to "1.14.4-28.2.26",
        11202 to "1.12.2-14.23.5.2859",
        11201 to "1.12.1-14.22.1.2478",
        11200 to "1.12-14.21.1.2387",
        11102 to "1.11.2-13.20.1.2588",
        11100 to "1.11-13.19.1.2189",
        11002 to "1.10.2-12.18.3.2511",
        10904 to "1.9.4-12.17.0.2317",
        10809 to "1.8.9-11.15.1.2318-1.8.9",
        10800 to "1.8-11.14.4.1563",
        10710 to "1.7.10-10.13.4.1614-1.7.10",
    ),
))

val revisionId = findProperty("essential.defaults.loom")?.toString() ?: throw GradleException("""
    No loom defaults version set.
    You need to set `essential.defaults.loom` in the project's `gradle.properties` file to a specific revision,
    so your build does not break when the defaults change.
    The recommended revision is always the most recent one, currently ${revisions.lastIndex}.
""".trimIndent())

val revision = revisions.getOrNull(revisionId.toIntOrNull() ?: -1)
    ?: throw GradleException("Invalid revision `$revisionId` for `essential.defaults.loom`. Latest is ${revisions.lastIndex}.")

fun prop(property: String, default: String?) =
    findProperty("essential.defaults.loom.$property")?.toString()
        ?: default
        ?: throw GradleException("No default $property for ${platform.mcVersionStr} ${platform.loaderStr}. Set `essential.defaults.loom.$property` in the project's `gradle.properties` or PR a new default.")

dependencies {
    minecraft(prop("minecraft", "com.mojang:minecraft:${platform.mcVersionStr}"))

    val mappingsStr = prop("mappings", when {
        platform.isFabric ->
            revision.yarn[platform.mcVersion]?.let { "net.fabricmc:yarn:$it" }
        platform.isForge && platform.mcVersion < 11700 ->
            revision.mcp[platform.mcVersion]?.let { "de.oceanlabs.mcp:mcp_$it" }
        else -> "official"
    })
    if (mappingsStr in listOf("official", "mojang", "mojmap")) {
        mappings(loom.officialMojangMappings())
    } else if (mappingsStr.isNotBlank()) {
        mappings(mappingsStr)
    }

    if (platform.isFabric) {
        modImplementation(prop("fabric-loader", "net.fabricmc:fabric-loader:${revision.fabricLoader}"))
    } else {
        "forge"(prop("forge", revision.forge[platform.mcVersion]?.let { "net.minecraftforge:forge:$it" }))

        loom.forge.pack200Provider.set(Pack200Adapter())
    }
}

// https://github.com/architectury/architectury-loom/pull/10
if (platform.isModLauncher) {
    val forgeRepo = repositories.find { it.name == "Forge" } as? MavenArtifactRepository
    forgeRepo?.metadataSources {
        mavenPom()
        artifact()
        ignoreGradleMetadataRedirection()
    }
}
