package io.github.alihansarigit.snoop.gradle

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration

/**
 * Keeps a Snoop desktop bridge alive for this project.
 *
 * The bridge has to be running before a developer taps "Send to project" on the device,
 * and asking them to remember a terminal command is what makes the feature rot. Instead
 * the start task is attached to the install tasks the IDE already runs, so pressing Run
 * is enough.
 */
class SnoopPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create("snoop", SnoopExtension::class.java).apply {
            autoStart.convention(true)
            port.convention(DEFAULT_PORT)
            projectDirectory.convention(project.rootProject.layout.projectDirectory)
            idleTimeoutMinutes.convention(DEFAULT_IDLE_TIMEOUT_MINUTES)
            daemonCoordinates.convention("io.github.alihansarigit:snoop-daemon:$SNOOP_VERSION")
        }

        val daemon = createDaemonConfiguration(project, extension)
        resolveDebugApplicationId(project, extension)

        val pidFile = project.layout.buildDirectory.file("snoop/bridge.pid")
        val startTask = project.tasks.register(
            "snoopBridge",
            SnoopBridgeStartTask::class.java,
        ) {
            group = GROUP
            description = "Starts the Snoop desktop bridge for this project if it is not running."
            daemonClasspath.from(daemon)
            port.set(extension.port)
            projectDirectory.set(extension.projectDirectory.map { it.asFile.absolutePath })
            applicationId.set(extension.applicationId)
            idleTimeoutMinutes.set(extension.idleTimeoutMinutes)
            logFile.set(project.layout.buildDirectory.file("snoop/bridge.log"))
            this.pidFile.set(pidFile)
            // Whether a bridge is listening is external state, so never skip the check.
            outputs.upToDateWhen { false }
        }

        project.tasks.register("snoopBridgeStop", SnoopBridgeStopTask::class.java) {
            group = GROUP
            description = "Stops the Snoop desktop bridge started from this project."
            this.pidFile.set(pidFile)
        }

        val autoStartDependency = project.provider {
            if (extension.autoStart.get()) listOf(startTask) else emptyList()
        }
        project.tasks.matching(::isDeviceInstallTask).configureEach {
            dependsOn(autoStartDependency)
        }
    }

    private fun createDaemonConfiguration(
        project: Project,
        extension: SnoopExtension,
    ): Configuration = project.configurations.create(DAEMON_CONFIGURATION) {
        isCanBeConsumed = false
        isCanBeResolved = true
        description = "Runtime classpath of the Snoop bridge daemon."
        // Only applies when the consumer has not added a daemon themselves, which is how
        // this repository points the sample at its own :daemon project.
        defaultDependencies {
            add(project.dependencies.create(extension.daemonCoordinates.get()))
        }
    }

    /**
     * Uses the debuggable variant's application id so the bridge rejects transfers from
     * anything else. With several debug variants the correct one is ambiguous, so the
     * restriction is dropped rather than guessed wrong.
     */
    private fun resolveDebugApplicationId(project: Project, extension: SnoopExtension) {
        val components = project.extensions
            .findByType(ApplicationAndroidComponentsExtension::class.java)
            ?: return
        val debugApplicationIds = project.objects.listProperty(String::class.java)
        components.onVariants { variant ->
            if (variant.buildType == DEBUG_BUILD_TYPE) {
                debugApplicationIds.add(variant.applicationId)
            }
        }
        extension.applicationId.convention(
            debugApplicationIds.map { ids -> ids.distinct().singleOrNull().orEmpty() },
        )
    }

    private fun isDeviceInstallTask(task: Task): Boolean =
        task.name.startsWith("install") && !task.name.endsWith("AndroidTest")

    private companion object {
        const val GROUP = "snoop"
        const val DAEMON_CONFIGURATION = "snoopDaemon"
        const val DEBUG_BUILD_TYPE = "debug"
        const val DEFAULT_PORT = 8737
        const val DEFAULT_IDLE_TIMEOUT_MINUTES = 480L
    }
}
