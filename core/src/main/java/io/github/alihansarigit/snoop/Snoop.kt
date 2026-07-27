package io.github.alihansarigit.snoop

import android.app.Application
import android.content.Context
import androidx.annotation.RestrictTo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import io.github.alihansarigit.snoop.internal.OverlayManager
import io.github.alihansarigit.snoop.internal.OverlayState
import io.github.alihansarigit.snoop.store.DiskPersistence
import io.github.alihansarigit.snoop.store.NetworkLogStore
import java.io.File

/**
 * Public entry point for Snoop.
 *
 * Snoop auto-installs through `androidx.startup`, so for most apps no code is
 * needed beyond adding the capture interceptor. The methods here let you control
 * the bubble and extend the logs dialog with custom sections, and [config] holds
 * runtime settings such as redaction and disk persistence.
 */
object Snoop {

    /**
     * Central runtime configuration — redaction, body decoding and disk persistence.
     * Safe to touch from any thread; changes apply to later captures.
     */
    @JvmField
    val config: SnoopConfig = SnoopConfig().apply {
        onPersistenceToggled = { enabled ->
            if (enabled) startPersistence() else stopPersistence()
        }
    }

    /** Shared capture store. Used by capture adapters; not part of the app-facing API. */
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    val store: NetworkLogStore = NetworkLogStore().apply { redactor = config::redact }

    /** Whether the floating bubble is shown. Backed by observable overlay state. */
    var enabled: Boolean
        get() = OverlayState.visible
        set(value) {
            OverlayState.visible = value
        }

    /**
     * Whether shaking the device brings a hidden bubble back on screen. Default `true`.
     * Set to `false` to disable it — e.g. if the host app has its own shake handling.
     * Takes effect immediately.
     */
    var shakeToShow: Boolean
        get() = OverlayState.shakeToShowEnabled
        set(value) {
            OverlayState.shakeToShowEnabled = value
            overlayManager?.onShakeSettingChanged()
        }

    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal val customSections = mutableStateListOf<DebugSection>()

    private var overlayManager: OverlayManager? = null
    private var appContext: Context? = null
    private var diskPersistence: DiskPersistence? = null

    /** Attach the overlay to every activity. Idempotent. */
    @JvmStatic
    fun install(application: Application) {
        if (overlayManager != null) return
        appContext = application.applicationContext
        val manager = OverlayManager()
        application.registerActivityLifecycleCallbacks(manager)
        overlayManager = manager
        if (config.persistenceEnabled) startPersistence()
    }

    /** Show the bubble again after [hide]. */
    @JvmStatic
    fun show() {
        OverlayState.visible = true
    }

    /** Hide the bubble (the "GİZLE" action does this). Restore with [show]. */
    @JvmStatic
    fun hide() {
        OverlayState.panelOpen = false
        OverlayState.visible = false
    }

    @JvmStatic
    fun clear() {
        store.clear()
    }

    /** Open the logs dialog directly (the bubble does this on tap). */
    @JvmStatic
    fun launchInspector(context: Context) {
        OverlayState.visible = true
        OverlayState.panelOpen = true
    }

    /**
     * Add a custom Compose section to the logs dialog — e.g. an environment switch
     * or a feature-flag toggle. Re-registering the same [title] replaces it.
     */
    fun registerSection(title: String, content: @Composable () -> Unit) {
        customSections.removeAll { it.title == title }
        customSections.add(DebugSection(title, content))
    }

    /**
     * Start disk persistence, restoring any retained transactions. Requires [install] to
     * have captured an application context (it has, once App Startup runs). Idempotent, and
     * invoked automatically when [SnoopConfig.persistenceEnabled] flips to `true`.
     */
    @Synchronized
    internal fun startPersistence() {
        if (diskPersistence != null) return
        val context = appContext ?: return
        val file = File(File(context.filesDir, "snoop"), "transactions.json")
        val persistence = DiskPersistence(file, config)
        store.restore(persistence.load())
        persistence.observe(store.transactions)
        diskPersistence = persistence
    }

    /** Stop disk persistence and release its background scope. Data already on disk is kept. */
    @Synchronized
    internal fun stopPersistence() {
        diskPersistence?.close()
        diskPersistence = null
    }
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal data class DebugSection(
    val title: String,
    val content: @Composable () -> Unit,
)
