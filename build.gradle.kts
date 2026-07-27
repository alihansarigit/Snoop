plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.maven.publish) apply false
}

// ─────────────────────────────────────────────────────────────────────────────
// Release convenience — one click bumps the patch version and publishes every
// library module (core, okhttp, ktor, no-op) to Maven Central.
//
//   ./gradlew releaseNextVersion   VERSION_NAME patch +1, then publish + release
//
// vanniktech reads VERSION_NAME at configuration time, so the bump must land
// before the publish is configured. bumpVersion rewrites gradle.properties, then
// releaseNextVersion runs the publish in a fresh nested build that re-reads the
// updated version. Running publish in a single nested invocation also bundles all
// modules into one Central Portal deployment.
// ─────────────────────────────────────────────────────────────────────────────
val bumpVersion by tasks.registering {
    group = "publishing"
    description = "Increments the patch component of VERSION_NAME in gradle.properties."
    doLast {
        val propsFile = rootProject.file("gradle.properties")
        val text = propsFile.readText()
        val match = Regex("""(?m)^VERSION_NAME=(\d+)\.(\d+)\.(\d+)$""").find(text)
            ?: throw GradleException("VERSION_NAME=x.y.z not found in gradle.properties")
        val (major, minor, patch) = match.destructured
        val old = "$major.$minor.$patch"
        val next = "$major.$minor.${patch.toInt() + 1}"
        propsFile.writeText(text.replaceRange(match.range, "VERSION_NAME=$next"))
        logger.lifecycle("Snoop version bumped: $old → $next")
    }
}

val releaseNextVersion by tasks.registering(GradleBuild::class) {
    group = "publishing"
    description = "Bumps the patch version and publishes all modules to Maven Central."
    dependsOn(bumpVersion)
    dir = rootDir
    tasks = listOf("publishAndReleaseToMavenCentral")
}
