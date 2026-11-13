package io.github.alihansarigit.snoop.internal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import android.graphics.Bitmap
import io.github.alihansarigit.snoop.diagnostics.UiTarget
import io.github.alihansarigit.snoop.feedback.FeedbackScreenContext
import io.github.alihansarigit.snoop.feedback.PendingCrash

internal enum class DebugDestination {
    CLOSED,
    HUB,
    INSPECTOR,
    CAPTURE_FEEDBACK,
    SESSIONS,
    NETWORK_TOOLS,
}

/** Process-wide, Compose-observable overlay state shared across all activities. */
internal object OverlayState {
    /** Bubble visibility. Toggled by Snoop.show()/hide(). */
    var visible by mutableStateOf(true)

    var destination by mutableStateOf(DebugDestination.CLOSED)

    var captureBusy by mutableStateOf(false)

    var captureBitmap by mutableStateOf<Bitmap?>(null)

    var captureTargets: List<UiTarget> = emptyList()

    var captureScreenContext: FeedbackScreenContext? = null

    /** Crash recovered from the previous process, if any. */
    var pendingCrash by mutableStateOf<PendingCrash?>(null)

    var pendingCrashBitmap by mutableStateOf<Bitmap?>(null)

    /** Drag offset of the bubble from its top-right anchor. */
    var bubbleOffset by mutableStateOf(Offset.Zero)

    /**
     * Whether a device shake re-shows a hidden bubble. Plain flag (read on the sensor
     * callback, not observed in composition); toggled via `Snoop.shakeToShow`.
     */
    @Volatile
    var shakeToShowEnabled: Boolean = true
}
