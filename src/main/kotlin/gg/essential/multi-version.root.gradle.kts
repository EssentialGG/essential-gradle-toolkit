package gg.essential

import gg.essential.gradle.util.checkJavaVersion

plugins {
    id("com.replaymod.preprocess-root")
}

checkJavaVersion(JavaVersion.VERSION_16)
