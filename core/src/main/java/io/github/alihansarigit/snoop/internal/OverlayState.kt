package io.github.alihansarigit.snoop.internal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

/** Process-wide, Compose-observable overlay state shared across all activities. */
internal object OverlayState {
    /** Bubble visibility. Toggled by Snoop.show()/hide(). */
    var visible by mutableStateOf(true)

    /** Whether the logs dialog is open. */
    var panelOpen by mutableStateOf(false)

    /** Drag offset of the bubble from its top-right anchor. */
    var bubbleOffset by mutableStateOf(Offset.Zero)

    /**
     * Whether a device shake re-shows a hidden bubble. Plain flag (read on the sensor
     * callback, not observed in composition); toggled via `Snoop.shakeToShow`.
     */
    @Volatile
    var shakeToShowEnabled: Boolean = true
}
