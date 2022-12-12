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
        11902 to "1.19.2+build.9:v2",
        11901 to "1.19.1+build.5:v2",
        11900 to "1.19+build.1:v2",
        11802 to "1.18.2+build.2:v2",
        11801 to "1.18.1+build.22:v2",
        11701 to "1.17.1+build.39:v2",
        11700 to "1.17+build.13:v2",
        11604 to "1.16.4+build.6:v2",
        11602 to "1.16.2+build.1:v2",
        11601 to "1.16.1+build.17:v2",
        11502 to "1.15.2+build.14",
        11404 to "1.14.4+build.16",
    ),
    mcp = mapOf(
        11602 to "snapshot:20201028-1.16.3",
        11502 to "snapshot:20200220-1.15.1@zip",
        11404 to "snapshot:20190719-1.14.3",
        11202 to "snapshot:20170615-1.12",
        11201 to "snapshot:20170615-1.12",
        11200 to "snapshot:20170615-1.12",
        11102 to "snapshot:20161220-1.11",
        11100 to "snapshot:20161111-1.10.2",
        11002 to "snapshot:20160518-1.9.4",
        10904 to "snapshot:20160518-1.9.4",
        10809 to "stable:22-1.8.9",
        10800 to "snapshot:20141130-1.8",
        10710 to "stable:12-1.7.10",
    ),
    fabricLoader = "0.13.3",
    forge = mapOf(
        11902 to "1.19.2-43.1.16",
        11900 to "1.19-41.0.63",
        11801 to "1.18.1-39.0.79",
        11701 to "1.17.1-37.0.112",
        11602 to "1.16.2-33.0.61",
        11502 to "1.15.2-31.1.18",
        11404 to "1.14.4-28.1.113",
        11202 to "1.12.2-14.23.0.2486",
        11201 to "1.12.1-14.22.0.2444",
        11200 to "1.12-14.21.1.2387",
        11102 to "1.11.2-13.20.0.2216",
        11100 to "1.11-13.19.1.2188",
        11002 to "1.10.2-12.18.2.2099",
        10904 to "1.9.4-12.17.0.1976",
        10809 to "1.8.9-11.15.1.2318-1.8.9",
        10800 to "1.8-11.14.4.1563",
        10710 to "1.7.10-10.13.4.1558-1.7.10",
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
