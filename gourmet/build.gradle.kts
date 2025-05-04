plugins {
    kotlin("jvm")
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("antlr")
    application
}

group = "io.github.seggan"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    antlr("org.antlr:antlr4:4.11.1")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass = "io.github.seggan.gourmet.MainKt"
}

tasks.generateGrammarSource {
    maxHeapSize = "128m"
    val path = File("$buildDir/generated-src/")
    val fullPath = path.resolve("antlr/main/io/github/seggan/gourmet/antlr/")
    doFirst {
        fullPath.mkdirs()
    }
    arguments = arguments + listOf(
        "-lib", fullPath.absoluteFile.toString(),
        "-visitor",
        "-no-listener",
        "-encoding", "UTF-8",
    )
    outputDirectory = fullPath
}

tasks.compileKotlin {
    dependsOn(tasks.generateGrammarSource)
}