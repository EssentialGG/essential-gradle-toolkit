package gg.essential.defaults

import org.gradle.api.tasks.compile.JavaCompile

plugins {
    java
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
