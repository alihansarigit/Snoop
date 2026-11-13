plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.maven.publish)
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.json.jvm)
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("io.github.alihansarigit.snoop.daemon.MainKt")
    applicationName = "snoop-mcp"
}
