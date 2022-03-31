package gg.essential

apply(plugin = "gg.essential.defaults.repo")
apply(plugin = "gg.essential.defaults.mixin-extras")

pluginManager.withPlugin("java") { apply(plugin = "gg.essential.defaults.java") }
pluginManager.withPlugin("gg.essential.loom") { apply(plugin = "gg.essential.defaults.loom") }
