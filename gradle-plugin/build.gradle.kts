plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    // Lets `java-gradle-plugin` emit the plugin marker, which is how a consumer outside
    // this repository resolves `id("io.github.alihansarigit.snoop")`.
    `maven-publish`
}

group = "io.github.alihansarigit"

// This is a separate build, so it does not inherit the library's gradle.properties.
// The version has to track it anyway — it decides which snoop-daemon the plugin resolves.
version = providers.gradleProperty("VERSION_NAME")
    .orElse(
        providers.fileContents(layout.projectDirectory.file("../gradle.properties"))
            .asText
            .map { text ->
                Regex("""(?m)^VERSION_NAME=(.+)$""")
                    .find(text)
                    ?.groupValues
                    ?.get(1)
                    ?.trim()
                    ?.ifBlank { null }
            },
    )
    .getOrElse("0.0.0-local")

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Read at configuration time only; the consumer's build supplies the real AGP.
    compileOnly("com.android.tools.build:gradle-api:8.13.2")
}

// The default daemon coordinates must track the plugin's own version, so bake it in
// rather than reading a jar manifest that does not exist in a composite build.
val generatedVersionDirectory = layout.buildDirectory.dir("generated/source/snoopVersion")

val generateSnoopVersion by tasks.registering {
    val versionName = version.toString()
    val outputDirectory = generatedVersionDirectory
    inputs.property("version", versionName)
    outputs.dir(outputDirectory)
    doLast {
        val file = outputDirectory.get()
            .file("io/github/alihansarigit/snoop/gradle/SnoopVersion.kt")
            .asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package io.github.alihansarigit.snoop.gradle

            internal const val SNOOP_VERSION: String = "$versionName"
            """.trimIndent() + "\n",
        )
    }
}

kotlin.sourceSets.named("main") {
    kotlin.srcDir(generateSnoopVersion)
}

gradlePlugin {
    plugins {
        create("snoop") {
            id = "io.github.alihansarigit.snoop"
            implementationClass = "io.github.alihansarigit.snoop.gradle.SnoopPlugin"
            displayName = "Snoop"
            description = "Keeps the Snoop desktop bridge running so a device can push " +
                "feedback sessions into this project."
        }
    }
}
