import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.alihansarigit.snoop.sample"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.alihansarigit.snoop.sample"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Demo only: sign release with the debug key so it can be assembled.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.okhttp)
    debugImplementation(libs.compose.ui.tooling)

    // Real overlay in debug, inert no-op in release.
    debugImplementation(project(":core"))
    debugImplementation(project(":okhttp"))
    releaseImplementation(project(":no-op"))
}

// ─────────────────────────────────────────────────────────────────────────────
// Emulator convenience tasks — bring up an AVD and run the sample on it.
//
//   ./gradlew runOnEmulator                       boot Pixel_8_Pro, install & launch the sample
//   ./gradlew runOnEmulator -Psnoop.avd=<name>  same, but pick a different AVD
//   ./gradlew bootEmulator                        only boot the AVD (or reuse a connected device)
//
// A device that is already connected is reused, so this is safe to re-run. The
// emulator keeps running after the build finishes.
// ─────────────────────────────────────────────────────────────────────────────
val emulatorAvd = (project.findProperty("snoop.avd") as String?) ?: "Pixel_8_Pro"
val sampleAppId = android.defaultConfig.applicationId
    ?: error("sample applicationId is not set")
val androidSdkDir = android.sdkDirectory
val emulatorLogFile = layout.buildDirectory.file("snoop-emulator-$emulatorAvd.log").get().asFile
val emulatorBootTimeoutMs = 180_000L

fun adbPath(): String = File(androidSdkDir, "platform-tools/adb").absolutePath
fun emulatorPath(): String = File(androidSdkDir, "emulator/emulator").absolutePath

/** Runs a command, merges stderr into stdout, and returns the trimmed output. */
fun captureOutput(vararg command: String): String {
    val process = ProcessBuilder(*command).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    process.waitFor()
    return output.trim()
}

/** Serials of devices currently in the `device` (fully online) state. */
fun onlineDevices(): List<String> =
    captureOutput(adbPath(), "devices")
        .lines()
        .filter { it.endsWith("\tdevice") }
        .map { it.substringBefore('\t').trim() }
        .filter { it.isNotEmpty() }

val bootEmulator by tasks.registering {
    group = "snoop"
    description = "Boots the '$emulatorAvd' AVD (or reuses a connected device) and waits until it is ready."
    doLast {
        val adb = adbPath()

        val connected = onlineDevices()
        if (connected.isNotEmpty()) {
            logger.lifecycle("✓ Device already connected (${connected.first()}) — skipping emulator boot.")
            return@doLast
        }

        val emulator = emulatorPath()
        val availableAvds = captureOutput(emulator, "-list-avds")
            .lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (emulatorAvd !in availableAvds) {
            throw GradleException(
                "AVD '$emulatorAvd' not found. Available AVDs: $availableAvds. " +
                    "Create one in Android Studio's Device Manager, or pass -Psnoop.avd=<name>."
            )
        }

        logger.lifecycle("▶ Booting emulator '$emulatorAvd' (log → $emulatorLogFile)…")
        emulatorLogFile.parentFile.mkdirs()
        ProcessBuilder(emulator, "-avd", emulatorAvd)
            .redirectOutput(emulatorLogFile)
            .redirectErrorStream(true)
            .start()

        val startedAt = System.currentTimeMillis()
        while (true) {
            val isOnline = onlineDevices().isNotEmpty()
            val bootCompleted = isOnline &&
                captureOutput(adb, "shell", "getprop", "sys.boot_completed") == "1"
            if (bootCompleted) break
            if (System.currentTimeMillis() - startedAt > emulatorBootTimeoutMs) {
                throw GradleException(
                    "Emulator '$emulatorAvd' did not finish booting within " +
                        "${emulatorBootTimeoutMs / 1000}s. See $emulatorLogFile."
                )
            }
            Thread.sleep(2_000)
        }

        // Wake the screen and dismiss the keyguard so the overlay is visible right away.
        captureOutput(adb, "shell", "input", "keyevent", "82")
        logger.lifecycle("✓ Emulator '$emulatorAvd' is booted and ready.")
    }
}

val runOnEmulator by tasks.registering {
    group = "snoop"
    description = "Boots '$emulatorAvd', installs the debug sample, and launches it."
    dependsOn(bootEmulator, "installDebug")
    doLast {
        val launchTarget = "$sampleAppId/.MainActivity"
        val result = captureOutput(adbPath(), "shell", "am", "start", "-n", launchTarget)
        if (result.contains("Error", ignoreCase = true)) {
            throw GradleException("Failed to launch $launchTarget:\n$result")
        }
        logger.lifecycle("✓ Launched $launchTarget — the Snoop bubble should appear top-right.")
    }
}

// installDebug needs a live device, so the emulator must be up first. `matching`
// keeps this lazy because AGP registers installDebug during afterEvaluate.
tasks.matching { it.name == "installDebug" }.configureEach {
    mustRunAfter(bootEmulator)
}
