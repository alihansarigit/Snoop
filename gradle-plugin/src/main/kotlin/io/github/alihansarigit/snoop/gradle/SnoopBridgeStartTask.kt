package io.github.alihansarigit.snoop.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Starts the desktop bridge as a detached process and returns immediately, so a build
 * that installs the app also leaves a bridge behind for the device to push to.
 *
 * Everything the action touches is captured as a task property; no `Project` is read at
 * execution time, which keeps the task usable with the configuration cache.
 */
abstract class SnoopBridgeStartTask : DefaultTask() {

    @get:InputFiles
    abstract val daemonClasspath: ConfigurableFileCollection

    @get:Input
    abstract val port: Property<Int>

    @get:Input
    abstract val projectDirectory: Property<String>

    @get:Input
    @get:Optional
    abstract val applicationId: Property<String>

    @get:Input
    abstract val idleTimeoutMinutes: Property<Long>

    @get:Internal
    abstract val logFile: RegularFileProperty

    @get:Internal
    abstract val pidFile: RegularFileProperty

    @TaskAction
    fun start() {
        val root = File(projectDirectory.get())
        val bridgePort = port.get()
        val packageName = applicationId.getOrElse("")

        when (val existing = BridgeProbe.inspect(bridgePort, root, packageName.ifBlank { PROBE_PACKAGE })) {
            is BridgeProbeResult.OwnedByThisProject -> {
                logger.lifecycle("✓ Snoop bridge already running on port $bridgePort.")
                return
            }
            is BridgeProbeResult.Foreign -> throw GradleException(
                "Port $bridgePort is held by another Snoop bridge — ${existing.description}. " +
                    "Feedback sent from the device would land in that project. Stop it, or give " +
                    "this project its own port with `snoop { port = <n> }` and set " +
                    "`Snoop.config.desktopBridgePort` to the same value.",
            )
            BridgeProbeResult.Free -> Unit
        }

        val classpath = daemonClasspath.files
        if (classpath.isEmpty()) {
            throw GradleException(
                "The Snoop daemon is missing. Add it to the `snoopDaemon` configuration or " +
                    "let the plugin resolve its default coordinates.",
            )
        }

        val log = logFile.get().asFile
        log.parentFile?.mkdirs()
        val arguments = buildList {
            add(File(System.getProperty("java.home"), "bin/java").absolutePath)
            add("-cp")
            add(classpath.joinToString(File.pathSeparator))
            add(DAEMON_MAIN_CLASS)
            add("bridge")
            add("--project-dir")
            add(root.absolutePath)
            add("--port")
            add(bridgePort.toString())
            add("--idle-timeout")
            add(idleTimeoutMinutes.get().toString())
            if (packageName.isNotBlank()) {
                add("--package")
                add(packageName)
            }
        }

        val process = ProcessBuilder(arguments)
            .redirectOutput(log)
            .redirectErrorStream(true)
            .start()
        pidFile.get().asFile.apply {
            parentFile?.mkdirs()
            writeText(process.pid().toString())
        }

        logger.lifecycle(
            "▶ Snoop bridge started on port $bridgePort → ${root.absolutePath}/agent-feedback " +
                "(log → $log)",
        )
        if (packageName.isBlank()) {
            logger.lifecycle(
                "  No debug application id was resolved, so the bridge accepts any package. " +
                    "Set `snoop { applicationId = \"…\" }` to restrict it.",
            )
        }
    }

    internal companion object {
        const val DAEMON_MAIN_CLASS = "io.github.alihansarigit.snoop.daemon.MainKt"

        /**
         * A bridge started for another project would reject an unknown package before
         * reporting its own path, which is exactly the collision we want to surface.
         */
        const val PROBE_PACKAGE = "io.github.alihansarigit.snoop.probe"
    }
}
