package io.github.alihansarigit.snoop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.alihansarigit.snoop.Snoop
import io.github.alihansarigit.snoop.internal.OverlayState
import io.github.alihansarigit.snoop.network.NetworkMockRegistry

@Suppress("DEPRECATION")
@Composable
internal fun DebugHubDialog(
    onInspector: () -> Unit,
    onCaptureFeedback: () -> Unit,
    onSessions: () -> Unit,
    onNetworkTools: () -> Unit,
    onClose: () -> Unit,
) {
    val transactions by Snoop.store.transactions.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val sessions = Snoop.feedbackSessionSnapshot()
    val activeSessionId = Snoop.activeSessionId()
    val activeSession = sessions.firstOrNull { it.id == activeSessionId }
    val errorCount = transactions.count { it.isError }
    val mockCount = NetworkMockRegistry.snapshot().size
    var logCaptureActive by remember { mutableStateOf(Snoop.isLogCaptureActive()) }
    var logCaptureStatus by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight(0.9f)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .background(NcColors.PanelBg, RoundedCornerShape(20.dp)),
            ) {
                DebugHubHeader(onClose = onClose)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        HubStatusPill("${transactions.size} requests", NcColors.Accent)
                        HubStatusPill(
                            if (errorCount == 0) "No errors" else "$errorCount errors",
                            if (errorCount == 0) NcColors.Ok else NcColors.Err,
                        )
                        HubStatusPill(
                            activeSession?.let { "Session · ${it.name.take(24)}" } ?: "No active session",
                            if (activeSession == null) NcColors.Muted else NcColors.Ok,
                        )
                    }

                    HubSectionLabel("PRIMARY TOOLS")
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val twoColumns = maxWidth >= 360.dp
                        if (twoColumns) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    DebugHubCard(
                                        icon = Icons.Filled.Search,
                                        title = "Inspector",
                                        subtitle = "${transactions.size} requests · $errorCount errors",
                                        accent = NcColors.Accent,
                                        onClick = onInspector,
                                        modifier = Modifier.weight(1f),
                                    )
                                    DebugHubCard(
                                        icon = Icons.Filled.CameraAlt,
                                        title = "Capture Feedback",
                                        subtitle = "Pin · crop · annotate · note",
                                        accent = Color(0xFF8B5CF6),
                                        loading = OverlayState.captureBusy,
                                        onClick = onCaptureFeedback,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    DebugHubCard(
                                        icon = Icons.Filled.FolderOpen,
                                        title = "Sessions",
                                        subtitle = "${sessions.size} total · ${activeSession?.items?.size ?: 0} active items",
                                        accent = NcColors.Ok,
                                        onClick = onSessions,
                                        modifier = Modifier.weight(1f),
                                    )
                                    DebugHubCard(
                                        icon = Icons.Filled.Http,
                                        title = "Network Tools",
                                        subtitle = "$mockCount mocks · replay in Inspector",
                                        accent = NcColors.Pending,
                                        onClick = onNetworkTools,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                DebugHubCard(
                                    Icons.Filled.Search,
                                    "Inspector",
                                    "${transactions.size} requests · $errorCount errors",
                                    NcColors.Accent,
                                    onClick = onInspector,
                                )
                                DebugHubCard(
                                    Icons.Filled.CameraAlt,
                                    "Capture Feedback",
                                    "Pin · crop · annotate · note",
                                    Color(0xFF8B5CF6),
                                    loading = OverlayState.captureBusy,
                                    onClick = onCaptureFeedback,
                                )
                                DebugHubCard(
                                    Icons.Filled.FolderOpen,
                                    "Sessions",
                                    "${sessions.size} total · ${activeSession?.items?.size ?: 0} active items",
                                    NcColors.Ok,
                                    onClick = onSessions,
                                )
                                DebugHubCard(
                                    Icons.Filled.Http,
                                    "Network Tools",
                                    "$mockCount mocks · replay in Inspector",
                                    NcColors.Pending,
                                    onClick = onNetworkTools,
                                )
                            }
                        }
                    }

                    HubSectionLabel("DIAGNOSTICS")
                    DebugHubCard(
                        icon = Icons.Filled.RadioButtonChecked,
                        title = if (logCaptureActive) "Stop Log Capture" else "Start Log Capture",
                        subtitle = if (logCaptureActive) {
                            "Recording · tap to stop and copy"
                        } else {
                            logCaptureStatus ?: "Capture up to 1,000 diagnostic lines"
                        },
                        accent = if (logCaptureActive) NcColors.Err else NcColors.Ok,
                        onClick = {
                            if (logCaptureActive) {
                                val lines = Snoop.stopLogCapture()
                                clipboard.setText(AnnotatedString(lines.joinToString("\n")))
                                logCaptureStatus = "${lines.size} lines copied"
                                logCaptureActive = false
                            } else {
                                Snoop.startLogCapture()
                                logCaptureStatus = null
                                logCaptureActive = true
                            }
                        },
                    )

                    if (Snoop.customSections.isNotEmpty()) {
                        HubSectionLabel("CUSTOM TOOLS")
                        Snoop.customSections.forEach { section ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = NcColors.RowBg,
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        section.title,
                                        color = NcColors.Text,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    section.content()
                                }
                            }
                        }
                    }

                    if (Snoop.developerActions.isNotEmpty()) {
                        HubSectionLabel("DEVELOPER ACTIONS")
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Snoop.developerActions.forEach { action ->
                                HubDeveloperAction(
                                    title = action.title,
                                    onClick = { action.action.run() },
                                )
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = NcColors.RowBg,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(onClick = {
                            onClose()
                            Snoop.hide()
                        }) {
                            Icon(
                                Icons.Filled.VisibilityOff,
                                contentDescription = null,
                                tint = NcColors.Muted,
                            )
                            Text(" Hide overlay", color = NcColors.Text)
                        }
                        Text(
                            "Shake to show: ${if (Snoop.shakeToShow) "ON" else "OFF"}",
                            color = NcColors.Muted,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugHubHeader(onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(NcColors.Accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.BugReport,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(23.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Snoop Debug Hub",
                color = NcColors.Text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Choose a tool",
                color = NcColors.Muted,
                fontSize = 13.sp,
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Close debug hub",
                tint = NcColors.Text,
            )
        }
    }
}

@Composable
private fun DebugHubCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    Surface(
        onClick = onClick,
        enabled = !loading,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 104.dp),
        shape = RoundedCornerShape(14.dp),
        color = NcColors.RowBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, NcColors.Border),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = accent,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                title,
                color = NcColors.Text,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                subtitle,
                color = NcColors.Muted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

@Composable
private fun HubStatusPill(text: String, color: Color) {
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(100.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        NcText(text, size = 10.sp, color = NcColors.Text, weight = FontWeight.Bold)
    }
}

@Composable
private fun HubSectionLabel(text: String) {
    NcText(text, size = 11.sp, color = NcColors.Muted, weight = FontWeight.Bold)
}

@Composable
private fun HubDeveloperAction(
    title: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp),
        shape = RoundedCornerShape(12.dp),
        color = NcColors.RowBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, NcColors.Border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(8.dp).background(NcColors.Toggle, CircleShape))
            Text(
                title,
                color = NcColors.Text,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text("RUN", color = NcColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}
