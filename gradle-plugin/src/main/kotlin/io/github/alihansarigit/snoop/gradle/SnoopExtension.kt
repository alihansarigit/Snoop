package io.github.alihansarigit.snoop.gradle

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property

/** Configuration for the Snoop desktop bridge, exposed to consumers as `snoop { … }`. */
abstract class SnoopExtension {

    /**
     * Start the bridge as part of `installDebug`, so pressing Run in the IDE is the
     * only thing a developer has to do. Default `true`.
     */
    abstract val autoStart: Property<Boolean>

    /** Loopback port shared with `SnoopConfig.desktopBridgePort`. Default `8737`. */
    abstract val port: Property<Int>

    /** Directory that receives `agent-feedback/<session-id>/`. Default: the root project. */
    abstract val projectDirectory: DirectoryProperty

    /**
     * Debug application id the bridge accepts transfers from. Derived from the single
     * debuggable variant; set it explicitly when the module has several flavors.
     */
    abstract val applicationId: Property<String>

    /**
     * Minutes of inactivity after which an auto-started bridge exits, so a background
     * process does not accumulate per project. `0` disables the watchdog. Default `480`.
     */
    abstract val idleTimeoutMinutes: Property<Long>

    /**
     * Maven coordinates of the daemon. Override to pin a version; add a dependency to
     * the `snoopDaemon` configuration directly to use a local build instead.
     */
    abstract val daemonCoordinates: Property<String>
}
