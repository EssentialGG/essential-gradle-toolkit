package gg.essential.gradle.util

import gg.essential.gradle.util.RelocationTransform.Companion.registerRelocationAttribute
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.kotlin.dsl.*

/**
 * Dependencies added to the returned configuration will be relocated, bundled directly into the jar file and not
 * included in the published maven pom.
 */
fun Project.makeConfigurationForInternalDependencies(name: String = "internal", configure: RelocationTransform.Parameters.() -> Unit): Configuration {
    // Create the configuration
    val configuration: Configuration by configurations.register(name)

    // and relocate everything which gets added to it
    val relocated = registerRelocationAttribute("$name-relocated", configure)
    configuration.attributes {
        attribute(relocated, true)
    }

    // and bundle it directly into the jar file
    tasks.named<AbstractArchiveTask>("jar") {
        dependsOn(configuration)
        from({ configuration.map { zipTree(it) } })
    }

    // We need to create a bundle jar for these so they do not get overwritten/upgrades by regular dependencies
    // E.g. If we have a relocated nightconfig, and modern forge provides a nightconfig of its own, simply extending the
    //      classpath configurations would combine those into one dependency, which is the opposite of what we want.
    val bundleConfiguration: Configuration by configurations.register(name + "Bundle")
    dependencies {
        bundleConfiguration(prebundle(configuration))
    }

    // and put them on the classpath but not on `implementation` directly, because that is included in the maven pom
    configurations.named(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME) { extendsFrom(bundleConfiguration) }
    configurations.named(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME) { extendsFrom(bundleConfiguration) }

    return configuration
}
