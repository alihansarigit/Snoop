package io.github.alihansarigit.snoop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.alihansarigit.snoop.Snoop
import io.github.alihansarigit.snoop.internal.OverlayState
import io.github.alihansarigit.snoop.model.TransactionStatus
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Root overlay rendered on top of every activity: a draggable debug bubble
 * anchored to the top-right, a request-count badge, and the logs [DebugLogsDialog].
 * The empty area is touch-transparent, so the host UI keeps working underneath.
 */
@Composable
internal fun SnoopOverlay() {
    if (!OverlayState.visible) return

    val transactions by Snoop.store.transactions.collectAsStateWithLifecycle()
    val count = transactions.size
    val badgeColor = when {
        transactions.any { it.isError } -> NcColors.Err
        transactions.any { it.status == TransactionStatus.PENDING } -> NcColors.Pending
        else -> NcColors.Ok
    }

    var drag by remember { mutableStateOf(OverlayState.bubbleOffset) }
    var dragDistance by remember { mutableStateOf(0f) }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 40.dp, end = 12.dp)
                .offset { IntOffset(drag.x.roundToInt(), drag.y.roundToInt()) },
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .shadow(8.dp, CircleShape)
                    .background(NcColors.Bubble, CircleShape)
                    .border(2.dp, Color.White, CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { dragDistance = 0f },
                            onDrag = { change, amount ->
                                change.consume()
                                drag = Offset(drag.x + amount.x, drag.y + amount.y)
                                OverlayState.bubbleOffset = drag
                                dragDistance += abs(amount.x) + abs(amount.y)
                            },
                            onDragEnd = { if (dragDistance < 10f) OverlayState.panelOpen = true },
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { OverlayState.panelOpen = true })
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.BugReport,
                    contentDescription = "Snoop",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }

            if (count > 0) {
                NcBadge(
                    count = count,
                    color = badgeColor,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp),
                )
            }
        }
    }

    if (OverlayState.panelOpen) {
        DebugLogsDialog(onDismiss = { OverlayState.panelOpen = false })
    }
}
