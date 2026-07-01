package io.github.alihansarigit.snoop

import android.app.Application
import android.content.Context
import androidx.annotation.RestrictTo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import io.github.alihansarigit.snoop.internal.OverlayManager
import io.github.alihansarigit.snoop.internal.OverlayState
import io.github.alihansarigit.snoop.store.NetworkLogStore

/**
 * Public entry point for Snoop.
 *
 * Snoop auto-installs through `androidx.startup`, so for most apps no code is
 * needed beyond adding the capture interceptor. The methods here let you control
 * the bubble and extend the logs dialog with custom sections.
 */
object Snoop {

    /** Shared capture store. Used by capture adapters; not part of the app-facing API. */
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    val store: NetworkLogStore = NetworkLogStore()

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

    /** Attach the overlay to every activity. Idempotent. */
    @JvmStatic
    fun install(application: Application) {
        if (overlayManager != null) return
        val manager = OverlayManager()
        application.registerActivityLifecycleCallbacks(manager)
        overlayManager = manager
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
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal data class DebugSection(
    val title: String,
    val content: @Composable () -> Unit,
)
