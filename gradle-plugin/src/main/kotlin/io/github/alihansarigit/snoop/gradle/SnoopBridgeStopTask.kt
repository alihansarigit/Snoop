package io.github.alihansarigit.snoop.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/** Stops the bridge this project started, using the pid recorded at start time. */
abstract class SnoopBridgeStopTask : DefaultTask() {

    @get:Internal
    abstract val pidFile: RegularFileProperty

    @TaskAction
    fun stop() {
        val file = pidFile.get().asFile
        val pid = file.takeIf { it.isFile }?.readText()?.trim()?.toLongOrNull()
        if (pid == null) {
            logger.lifecycle("No Snoop bridge was started from this project.")
            return
        }
        val handle = ProcessHandle.of(pid).orElse(null)
        if (handle == null || !handle.isAlive) {
            logger.lifecycle("Snoop bridge (pid $pid) is no longer running.")
        } else {
            handle.destroy()
            logger.lifecycle("■ Snoop bridge stopped (pid $pid).")
        }
        file.delete()
    }
}
