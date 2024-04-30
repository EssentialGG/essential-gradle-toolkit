package gg.essential.gradle.multiversion

import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Project

data class Platform(
    val mcMajor: Int,
    val mcMinor: Int,
    val mcPatch: Int,
    val loader: Loader,
) {
    val mcVersion = mcMajor * 10000 + mcMinor * 100 + mcPatch
    val mcVersionStr = listOf(mcMajor, mcMinor, mcPatch).dropLastWhile { it == 0 }.joinToString(".")
    val loaderStr = loader.toString().toLowerCase()

    val isFabric = loader == Loader.Fabric
    val isForge = loader == Loader.Forge
    val isNeoForge = loader == Loader.NeoForge
    val isForgeLike = isForge || isNeoForge
    val isModLauncher = loader == Loader.Forge && mcVersion >= 11400
    val isLegacyForge = loader == Loader.Forge && mcVersion < 11400

    val javaVersion = when {
        mcVersion >= 12005 -> JavaVersion.VERSION_21
        mcVersion >= 11800 -> JavaVersion.VERSION_17
        mcVersion >= 11700 -> JavaVersion.VERSION_16
        else -> JavaVersion.VERSION_1_8
    }

    override fun toString(): String {
        return "$mcVersionStr-$loaderStr"
    }

    enum class Loader {
        Fabric,
        Forge,
        NeoForge,
    }

    companion object {

        fun of(project: Project): Platform {
            val loader = guessLoader(project)
            val mcVersionStr = guessMcVersion(project)
            val (major, minor, patch) = mcVersionStr.split('.').map { it.toInt() } + listOf(0)
            return Platform(major, minor, patch, loader)
        }

        private fun guessMcVersion(project: Project): String {
            // Try configured minecraft.version value first
            project.findProperty("minecraft.version")
                ?.let { return it.toString() }

            // If that's not set, try to infer it from the project name
            Regex("""(\d+)\.(\d+)(\.(\d+))?""").find(project.name)
                ?.let { return it.value }

            throw GradleException(
                "Failed to infer Minecraft version for project \"${project.path}\".\n" +
                        "Either set \"minecraft.version\" in its \"gradle.properties\"," +
                        "or change the project name to include the version."
            )
        }

        private fun guessLoader(project: Project): Loader {
            // Try configured loom.platform value first
            val loomPlatform = project.findProperty("loom.platform")?.toString()
            when (loomPlatform?.lowercase()) {
                "fabric" -> return Loader.Fabric
                "forge" -> return Loader.Forge
                "neoforge" -> return Loader.NeoForge
                null -> {}
                else -> throw GradleException("Unknown loom.platform value: \"$loomPlatform\"")
            }

            // If that's not set, try to infer it from the project name
            when {
                "fabric" in project.name.lowercase() -> return Loader.Fabric
                "neoforge" in project.name.lowercase() -> return Loader.NeoForge
                "forge" in project.name.lowercase() -> return Loader.Forge
                else -> {}
            }

            throw GradleException("Failed to infer mod loader for project \"${project.path}\".\n" +
                    "Either set \"loom.platform\" in its \"gradle.properties\"," +
                    "or change the project name to include the platform.\n" +
                    "Valid values: ${Loader.values().joinToString { it.name.toLowerCase() }}")
        }
    }
}
