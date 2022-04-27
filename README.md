# Essential Gradle Toolkit
A Gradle plugin providing various utility methods and common code required to set up multi-version Minecraft mods via [architectury-loom] and [preprocessor].

### Dependency
<img alt="version badge" src="https://badges.modcore.net/maven-metadata/v?metadataUrl=https://repo.essential.gg/repository/maven-public/gg/essential/essential-gradle-toolkit/maven-metadata.xml">

To use essential-gradle-toolkit in your project, you need to add the following repositories to your `settings.gradle(.kts)` file:
```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://repo.essential.gg/repository/maven-public")
        maven("https://maven.architectury.dev")
        maven("https://maven.fabricmc.net")
        maven("https://maven.minecraftforge.net")
    }
    // We also recommend specifying your desired version here if you're using more than one of the plugins,
    // so you do not have to change the version in multilpe places when updating.
    plugins {
        val egtVersion = "0.1.0" // should be whatever is displayed in above badge
        id("gg.essential.multi-version.root") version egtVersion
        id("gg.essential.multi-version.api-validation") version egtVersion
    }
}
```

## Plugins

## gg.essential.multi-version
This is the main plugin enabling multi-version mods.
To create a project which gets compiled for multiple versions, create multiple sub-projects within your main Gradle project:
<details>
<summary>settings.gradle.kts</summary>

```kotlin
listOf(
    "1.8.9-forge",
    "1.12.2-forge",
    "1.16.2-forge",
    "1.16.2-fabric",
).forEach { version ->
    include(":$version")
    project(":$version").apply {
        // This is where the `build` folder and per-version overwrites will reside
        projectDir = file("versions/$version")
        // All sub-projects get configured by the same `build.gradle.kts` file, the string is relative to projectDir
        // You could use separate build files for each project, but usually that would just be duplicating lots of code
        buildFileName = "../../build.gradle.kts"
    }
}

// We use the `build.gradle.kts` file for all the sub-projects (cause that's where most the interesting stuff lives),
// so we need to use a different build file for the original root project.
rootProject.buildFileName = "root.gradle.kts"
```
</details>
<details>
<summary>root.gradle.kts</summary>

```kotlin
plugins {
    // This marks the current project as the root of a multi-version project.
    // Any project using `gg.essential.multi-version` must have a parent with this root plugin applied.
    // Advanced users may use multiple (potentially independent) multi-version trees in different sub-projects.
    // This is currently equivalent to applying `com.replaymod.preprocess-root`.
    id("gg.essential.multi-version.root")
}

preprocess {
    // Here you first need to create a node per version you support and assign it an integer Minecraft version.
    // The mappings value is currently meaningless.
    val fabric11602 = createNode("1.16.2-fabric", 11602, "yarn")
    val forge11602 = createNode("1.16.2-forge", 11602, "mcp")
    val forge11202 = createNode("1.12.2-forge", 11202, "mcp")
    val forge10809 = createNode("1.8.9-forge", 10809, "mcp")

    // And then you need to tell the preprocessor which versions it should directly convert between.
    // This should form a directed graph with no cycles (i.e. a tree), which the preprocessor will then traverse to
    // produce source code for all versions from the main version.
    // Do note that the preprocessor can only convert between two projects when they are either on the same Minecraft
    // version (but use different mappings, e.g. 1.16.2 forge to fabric), or when they are using the same intermediary
    // mappings (but on different Minecraft versions, e.g. 1.12.2 forge to 1.8.9 forge, or 1.16.2 fabric to 1.18 fabric)
    // but not both at the same time, i.e. you cannot go straight from 1.12.2 forge to 1.16.2 fabric, you need to go via
    // an intermediary 1.16.2 forge project which has something in common with both.
    fabric11602.link(forge11602)
    forge11602.link(forge11202)
    // For any link, you can optionally specify a file containing extra mappings which the preprocessor cannot infer by
    // itself, e.g. forge intermediary names do not contain class names, so you may need to supply mappings for those
    // manually.
    forge11202.link(forge10809, file("versions/1.12.2-1.8.9.txt"))
}
```
</details>
<details>
<summary>build.gradle.kts</summary>

```kotlin
plugins {
    // If you're using Kotlin, it needs to be applied before the multi-version plugin
    kotlin("jvm")
    // Apply the multi-version plugin, this does all the configuration necessary for the preprocessor to
    // work. In particular it also applies `com.replaymod.preprocess`.
    // In addition it primarily also provides a `platform` extension which you can use in this build script
    // to get the version and mod loader of the current project.
    id("gg.essential.multi-version")
    // If you do not care too much about the details, you can just apply essential-gradle-toolkits' defaults for
    // Minecraft, fabric-loader, forge, mappings, etc. versions.
    // You can also overwrite some of these if need be. See the `gg.essential.defaults.loom` README section.
    // Otherwise you'll need to configure those as usual for (architectury) loom.
    id("gg.essential.defaults")
}

dependencies {
    // If you are depending on a multi-version library following the same scheme as the Essential libraries (that is
    // e.g. `elementa-1.8.9-forge`), you can `toString` `platform` directly to get the respective artifact id.
    modImplementation("gg.essential:elementa-$platform:428")
}

tasks.processResources {
    // Expansions are already set up for `version` (or `file.jarVersion`) and `mcVersionStr`.
    // You do not need to set those up manually.
}

loom {
    // If you need to use a tweaker on legacy (1.12.2 and below) forge:
    if (platform.isLegacyForge) {
        launchConfigs.named("client") {
            arg("--tweakClass", "gg.essential.loader.stage0.EssentialSetupTweaker")
            // And maybe a core mod?
            property("fml.coreMods.load", "com.example.asm.CoreMod")
        }
    }
    // Mixin on forge? (for legacy forge you will still need to register a tweaker to set up mixin)
    if (platform.isForge) {
        forge {
            mixinConfig("example.mixins.json")
            // And maybe an access transformer?
            // Though try to avoid these, cause they are not automatically translated to Fabric's access widener
            accessTransformer(project.parent.file("src/main/resources/example_at.cfg"))
        }
    }
}
```
</details>

Finally you'll have to create a file at `/versions/mainProject` which contains the name of your main project (the one with its sources in `/src`), and you should be good to go:
<details>
<summary>versions/mainProject</summary>

```
1.12.2-forge
```
</details>

### gg.essential.multi-version.root
See the comments in the `root.gradle.kts` file above.

### gg.essential.multi-version.api-validation
This plugin builds on Kotlin's [binary-compatibility-validator] to prevent accidental changes to your public ABI.

It combines all the per-version api files generated by the base plugin into a single file in `/api/Example.api`, thereby
avoiding the redundancy you would have if you were to use the [binary-compatibility-validator] plugin directly in all
the sub-projects.
It takes in a sense the same role as the [preprocessor] takes for Java/Kotlin files.

It should be applied and configured in the root project and will configure the sub-projects by itself:
<details>
<summary>root.gradle.kts</summary>

```kotlin
plugins {
    id("gg.essential.multi-version.root")
    id("gg.essential.multi-version.api-validation")
}

apiValidation {
    ignoredPackages.add("com.example")
}
```
</details>

The per-project api files which the base plugin generates should be added to your `.gitignore`:
<details>
<summary>.gitignore</summary>

```gitignore
versions/*/api/
```
</details>

## gg.essential.defaults

Applies various (partially opinionated) defaults to your project:
- [repo](#ggessentialdefaultsrepo)
- [mixin-extras](#ggessentialdefaultsmixin-extras)
- [java](#ggessentialdefaultsjava) (if the `java` plugin is applied)
- [loom](#ggessentialdefaultsloom) (if the `gg.essential.loom` plugin is applied)

Does not apply:
- [maven-publish](#ggessentialdefaultsmaven-publish)

### gg.essential.defaults.java

Sets defaults related to the `java` Gradle plugin:
- encoding to UTF-8

### gg.essential.defaults.loom

Sets defaults related to the `gg.essential.loom` ([architectury-loom]) Gradle plugin.
You can overwrite all of these by setting the given property in the project's `gradle.properties`:
- Minecraft version (`essential.defaults.loom.minecraft`)
- Mappings (`essential.defaults.loom.mappings`), special values:
    - `official`/`mojang`/`mojmap`: Uses `loom.officialMojangMappings()`
    - empty string: skips mappings completely so you can configure layered mappings
- Fabric-Loader version (`essential.defaults.loom.fabric-loader`)
- Forge version (`essential.defaults.loom.forge`)

Note that these may change frequently. To avoid your build breaking when they do, you need to set the
`essential.defaults.loom` property in the (root) project's `gradle.properties` file to a specific revision.
If you build without specifying this property, the build will fail and it will tell you which revision is the one
currently recommended.

### gg.essential.defaults.mixin-extras

Enables use of Essential's version of [MixinExtras], requires Essential to be present at compile time and runtime to function.

### gg.essential.defaults.repo

Adds Essential and MavenCentral repos to the project.

### gg.essential.defaults.maven-publish

Configures the maven-publish plugin for use with Essential's maven repository. This is likely only useful for libraries
published on Essential's maven.

If the multi-version plugin is applied, the artifactId is set to follow the `name-version-loader` scheme (e.g.
`elementa-1.12.2-forge`) where `name` is inferred from the root project's name. Make sure to set it in your
`settings.gradle.kts` because otherwise Gradle will default to the directory name, which may not be reliable.

Also configures Loom to publish the named jars for legacy Forge versions, rather than the intermediary-mapped ones,
because that seems to be common practice for those versions.

## Non-plugins

Various utility functions are provided in the `gg.essential.gradle.util` package.

### Prebundle

Bundles all dependencies from a given Gradle configuration into a single, dedicated jar and returns a file collection
containing that jar.

Primarily for use in dependency declarations, so fat jars of certain dependencies (with potentially relocated
transitive dependencies) can be created and then depended upon as usual. Compared to simply relocating in a later
shadow task, this has the advantage that IDEA will see the relocated dependency rather than the original, which e.g.
allows one to use two different versions of the same dependency at dev time.

This may also be useful if you wish to have custom class loaders, the content of which you need to control precisely.
See [the method docs](src/main/kotlin/gg/essential/gradle/util/prebundle.kt) for more details.

```kotlin
import gg.essential.gradle.util.prebundle

dependencies {
    // Creating a named configuration because the bundled jar will take its name from it, e.g. `bothLibs.jar`
    val bothLibs by configurations.creating
    bothLibs("com.google.code.gson:gson:2.0.0")
    bothLibs("com.example:libRequiringAnAncientGson:1.0.0")
    implementation(prebundle(bothLibs))
}
```

### RelocationTransform

A [Gradle artifact transform] which relocates packages and files.
Usually used with [prebundle](#prebundle).

See [the docs on the class](src/main/kotlin/gg/essential/gradle/util/RelocationTransform.kt) for more details.

```kotlin
import gg.essential.gradle.util.RelocationTransform.Companion.registerRelocationAttribute

val relocated = registerRelocationAttribute("relocate-ancient-gson") {
    relocate("com.google.gson", "com.example.lib.gson")
}

val ancientGson by configurations.creating {
    attributes { attribute(relocated, true) }
}

dependencies {
    ancientGson("com.google.code.gson:gson:2.0.0")
    ancientGson("com.example:libRequiringAnAncientGson:1.0.0")
    implementation(prebundle(ancientGson))
}
```

### `internal` configuration

The [`makeConfigurationForInternalDependencies` method](src/main/kotlin/gg/essential/gradle/util/internalConfiguration.kt) creates a new configuration (by default named `internal`) which can be used to declare dependencies which are completely internal to the project.

These dependencies will automatically be included in the generated jar file and won't be visible in the maven metadata.\
Most importantly they can (and should!) also be relocated to your package, so they do not conflict with other mods bundling the same code.

```kotlin
val internal = makeConfigurationForInternalDependencies {
    relocate("org.commonmark", "com.example.libs.commonmark")
}

dependencies {
    internal("org.commonmark:commonmark:0.17.1")
    internal("org.commonmark:commonmark-ext-gfm-strikethrough:0.17.1")
    internal("org.commonmark:commonmark-ext-ins:0.17.1")
}
```

### common project
If your project has a significant amount of platform/version-independent code, it may be advisable to extract that code into a `common` sub-project, so it only needs to be compiled once.

You can do this using standard Gradle procedures.
Some additional utilities are provided here to improve the scope of which code can be considered common.

#### [StripReferencesTransform](src/main/kotlin/gg/essential/gradle/multiversion/StripReferencesTransform.kt)

If you depend on libraries which have roughly the same ABI across versions (not referencing any Minecraft classes in most of it), like [UniversalCraft] and [Elementa], it may be tempting to directly depend on those from your common project.
But this will fail when you try to extend classes which reference Minecraft classes because the compiler should not be able to see those.

This [Gradle artifact transform] removes all references to classes within a given package, thereby fixing this issue.
```kotlin
val common = registerStripReferencesAttribute("common") {
    excludes.add("net.minecraft")
}

dependencies {
    // Using `compileOnly` because we only want to compile against the "common" UniversalCraft jar,
    // we don't want it to be present at runtime.
    // No remapping is necessary because we plan to strip all references to Minecraft anyway.
    // The specific Minecraft version which one depends on doesn't really matter. It is generally advisable to use the
    // oldest version one supports, so one does not accidentally use methods only available in newer versions.
    compileOnly("gg.essential:universalcraft-1.8.9-forge:master-SNAPSHOT") {
        // Setting the attribute to `true` will cause the transformer to apply to this specific artifact
        attributes { attribute(common, true) }
    }
}
```

#### [mergePlatformSpecifics](src/main/kotlin/gg/essential/gradle/multiversion/mergePlatformSpecifics.kt)

If the vast majority of your code is platform-independent
but you have a small amount of API methods (where removing them would constitute a breaking change) which depend on platform-specific types,
this would ordinarily prevent you from moving the entire file (and any files depending on it) to the common project.

For such cases, this method allows you to define a class with the same name (suffixed with `_platform`) in your
platform-specific projects and declare the API methods in there.
Then, after the common classes have been combined into a single jar file with the platform-specific code, this method
can be called on the jar file to merge the platform-specific classes into the common ones.

```kotlin
tasks.jar {
    from(/* ... */) // add your common code in whatever way you see fit
    mergePlatformSpecifics() // enable merging of platform-specific files
}
```

This preserves the ABI of published artifacts but does not allow those methods to be used in your development environment (because they are only merged at build time).
If that is something you need, you should move the method implementation to an internal method, use that throughout your project, and have the API method simply delegate to it.
If you wish to run a third-party mod which depends on the API method in your development environment, you're out of luck.

#### [excludeKotlinDefaultImpls](src/main/kotlin/gg/essential/gradle/multiversion/excludeKotlinDefaultImpls.kt)

Removes Kotlin's `$DefaultImpls` classes (and any references to them) from the given jar file as if the Kotlin code
was compiled with `-Xjvm-default=all`.

This is useful if you have a platform-independent "common" project containing the vast majority of your code but, to
maintain backwards compatibility, you need to compile with `-Xjvm-default=all-compatibility` for some platforms.
Ordinarily this would then leak the DefaultImpls to all platforms and you'll be stuck with `all-compatibility` for
all of them (even when that would not have been required for modern versions).

For such cases, this method allows you to strip the `$DefaultImpls` classes from a given platform-specific jar file:
```kotlin
tasks.jar {
    if (platform.mcVersion >= 11400) {
        excludeKotlinDefaultImpls()
    }
}
```

### versionFromBuildIdAndBranch

Generates a simple project version based on the current branch and the `BUILD_ID` property (for CI builds) according to the following schema.

| branch   | CI build   | Local build       |
|:---------|------------|-------------------|
| `master` | `42`       | `master-SNAPSHOT` |
| other    | `42+other` | `other-SNAPSHOT`  |

### extensions

Miscellaneous extension functions to avoid having to write the same thing in multiple projects.
See [the file](src/main/kotlin/gg/essential/gradle/util/extensions.kt) for details.

## License
The essential-gradle-toolkit is provided under the terms of the GNU General Public License Version 3 or (at your option) any later version.
See `LICENSE.md` for the full license text.

[architectury-loom]: https://github.com/Sk1erLLC/architectury-loom
[preprocessor]: https://github.com/ReplayMod/preprocessor
[binary-compatibility-validator]: https://github.com/Kotlin/binary-compatibility-validator
[MixinExtras]: https://github.com/LlamaLad7/MixinExtras
[Gradle artifact transform]: https://docs.gradle.org/current/userguide/artifact_transforms.html
[UniversalCraft]: https://github.com/EssentialGG/UniversalCraft
[Elementa]: https://github.com/EssentialGG/Elementa
