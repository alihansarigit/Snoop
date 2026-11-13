package io.github.alihansarigit.snoop.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.alihansarigit.snoop.Snoop
import io.github.alihansarigit.snoop.feedback.FeedbackAttachment
import io.github.alihansarigit.snoop.feedback.FeedbackItem
import io.github.alihansarigit.snoop.feedback.FeedbackPin
import io.github.alihansarigit.snoop.feedback.FeedbackSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class AttachmentPreview(
    val sessionId: String,
    val attachment: FeedbackAttachment,
    val title: String,
)

private data class PinSelectionKey(
    val itemId: String,
    val pinId: Int,
)

private sealed interface ProjectBridgeUiState {
    data object Checking : ProjectBridgeUiState

    data class Disconnected(val message: String) : ProjectBridgeUiState

    data class Connected(
        val projectName: String,
        val projectPath: String,
        val lastExportPath: String? = null,
        val transferError: String? = null,
    ) : ProjectBridgeUiState

    data class Transferring(val connection: Connected) : ProjectBridgeUiState
}

@Composable
internal fun SessionManagerDialog(
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var revision by remember { mutableStateOf(0) }
    var selectedSessionId by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<FeedbackSession?>(null) }
    var attachmentPreview by remember { mutableStateOf<AttachmentPreview?>(null) }
    var exportingSessionId by remember { mutableStateOf<String?>(null) }
    var downloadingSessionId by remember { mutableStateOf<String?>(null) }
    var bridgeState by remember {
        mutableStateOf<ProjectBridgeUiState>(ProjectBridgeUiState.Checking)
    }
    val sessions = remember(revision) { Snoop.feedbackSessionSnapshot() }
    val activeId = remember(revision) { Snoop.activeSessionId() }
    val selectedSession = sessions.firstOrNull { it.id == selectedSessionId }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun checkDesktopBridge() {
        bridgeState = ProjectBridgeUiState.Checking
        scope.launch {
            val connection = withContext(Dispatchers.IO) { Snoop.desktopBridgeStatus() }
            bridgeState = if (connection.connected) {
                ProjectBridgeUiState.Connected(
                    projectName = connection.projectName,
                    projectPath = connection.projectPath,
                )
            } else {
                ProjectBridgeUiState.Disconnected(connection.message)
            }
        }
    }

    fun sendToProject(session: FeedbackSession) {
        val connected = bridgeState as? ProjectBridgeUiState.Connected ?: return
        bridgeState = ProjectBridgeUiState.Transferring(connected)
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    Snoop.sendSessionToProject(session.id)
                }
            }.onSuccess { exported ->
                bridgeState = ProjectBridgeUiState.Connected(
                    projectName = exported.projectName,
                    projectPath = exported.projectPath,
                    lastExportPath = exported.exportPath,
                )
                Toast.makeText(
                    context,
                    "Session transferred to ${exported.projectName}",
                    Toast.LENGTH_SHORT,
                ).show()
            }.onFailure { error ->
                bridgeState = connected.copy(
                    transferError = error.message ?: "Project transfer failed",
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        checkDesktopBridge()
    }

    fun copySession(session: FeedbackSession) {
        Snoop.exportSession(session.id)?.let { markdown ->
            val attachmentCount = FeedbackClipboard.copySession(context, session, markdown)
            Toast.makeText(
                context,
                "Session copied · $attachmentCount attachment${if (attachmentCount == 1) "" else "s"}",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun copyItem(sessionId: String, item: FeedbackItem) {
        Snoop.exportFeedbackItem(sessionId, item.id)?.let { markdown ->
            val attachmentCount = FeedbackClipboard.copyItem(context, sessionId, item, markdown)
            Toast.makeText(
                context,
                "Feedback item copied · $attachmentCount attachment${if (attachmentCount == 1) "" else "s"}",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun copyAttachment(
        sessionId: String,
        attachment: FeedbackAttachment,
        label: String,
    ) {
        FeedbackClipboard.copyAttachment(context, sessionId, attachment, label)
        Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
    }

    fun exportForAgent(session: FeedbackSession) {
        if (exportingSessionId != null) return
        exportingSessionId = session.id
        scope.launch {
            val bundlePath = withContext(Dispatchers.IO) {
                Snoop.createAgentBundle(session.id)
            }
            exportingSessionId = null
            if (bundlePath == null) {
                Toast.makeText(
                    context,
                    "Agent export could not be created",
                    Toast.LENGTH_SHORT,
                ).show()
                return@launch
            }
            runCatching {
                FeedbackShare.exportAgentBundle(
                    context = context,
                    sessionId = session.id,
                    bundlePath = bundlePath,
                    sessionName = session.name,
                )
            }.onFailure {
                Toast.makeText(context, "No app can export this bundle", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun downloadForAgent(session: FeedbackSession) {
        if (downloadingSessionId != null) return
        downloadingSessionId = session.id
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val bundleName = checkNotNull(Snoop.createAgentBundle(session.id)) {
                        "Agent export could not be created"
                    }
                    val bytes = checkNotNull(Snoop.feedbackAttachment(session.id, bundleName)) {
                        "Agent bundle could not be read"
                    }
                    FeedbackDownload.saveAgentBundle(context, bundleName, bytes)
                }
            }
            downloadingSessionId = null
            Toast.makeText(
                context,
                saved.fold(
                    onSuccess = { path -> "Saved to $path" },
                    onFailure = { error -> error.message ?: "Download failed" },
                ),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun pinSelection(selectedPins: Set<PinSelectionKey>): Map<String, Set<Int>> = selectedPins
        .groupBy(PinSelectionKey::itemId)
        .mapValues { (_, keys) -> keys.map(PinSelectionKey::pinId).toSet() }

    fun shareSelectedPins(
        session: FeedbackSession,
        selectedPins: Set<PinSelectionKey>,
    ) {
        val payload = Snoop.exportSelectedPins(session.id, pinSelection(selectedPins))
        if (payload == null) {
            Toast.makeText(context, "Select at least one pin", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            FeedbackShare.shareSelectedPins(
                context = context,
                sessionId = session.id,
                sessionName = session.name,
                payload = payload,
            )
        }.onFailure {
            Toast.makeText(context, "No app can share the selected pins", Toast.LENGTH_SHORT).show()
        }
    }

    fun sendSelectedPinsToClaude(
        session: FeedbackSession,
        selectedPins: Set<PinSelectionKey>,
    ) {
        val payload = Snoop.exportSelectedPins(session.id, pinSelection(selectedPins))
        if (payload == null) {
            Toast.makeText(context, "Select at least one pin", Toast.LENGTH_SHORT).show()
            return
        }
        val delivered = ClaudeAppShare.sendSelectedPins(
            context = context,
            sessionId = session.id,
            sessionName = session.name,
            payload = payload,
        )
        if (!delivered) {
            // Never dead-end the button: fall back to the chooser so the pins still travel.
            Toast.makeText(
                context,
                "Claude app not found — pick another target",
                Toast.LENGTH_SHORT,
            ).show()
            shareSelectedPins(session, selectedPins)
        }
    }

    fun sendSelectedPinsToProject(
        session: FeedbackSession,
        selectedPins: Set<PinSelectionKey>,
    ) {
        val connected = bridgeState as? ProjectBridgeUiState.Connected ?: return
        bridgeState = ProjectBridgeUiState.Transferring(connected)
        val selection = pinSelection(selectedPins)
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    Snoop.sendSelectedPinsToProject(session.id, selection)
                }
            }.onSuccess { exported ->
                bridgeState = ProjectBridgeUiState.Connected(
                    projectName = exported.projectName,
                    projectPath = exported.projectPath,
                    lastExportPath = exported.exportPath,
                )
                Toast.makeText(
                    context,
                    "${selectedPins.size} pin sent to ${exported.projectName}",
                    Toast.LENGTH_SHORT,
                ).show()
            }.onFailure { error ->
                bridgeState = connected.copy(
                    transferError = error.message ?: "Project transfer failed",
                )
            }
        }
    }

    Dialog(
        onDismissRequest = {
            if (selectedSessionId == null) onBack() else selectedSessionId = null
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(12.dp),
            color = NcColors.PanelBg,
            contentColor = NcColors.Text,
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SessionHeader(
                    title = selectedSession?.name ?: "Feedback sessions",
                    subtitle = selectedSession?.let {
                        "${it.items.size} feedback · ${if (it.closedAt == null) "Open" else "Closed"}"
                    } ?: "${sessions.size} session",
                    onBack = {
                        if (selectedSessionId == null) onBack() else selectedSessionId = null
                    },
                    onClose = onClose,
                )
                // Scrolls with the list rather than pinning the top of the dialog — on a
                // short screen the fixed card left too little room for the content.
                val bridgeCard: @Composable () -> Unit = {
                    ProjectBridgeCard(
                        state = bridgeState,
                        packageName = context.packageName,
                        sessionName = selectedSession?.name,
                        onRetry = ::checkDesktopBridge,
                        onTransfer = {
                            selectedSession?.let(::sendToProject)
                        },
                    )
                }
                if (selectedSession == null) {
                    SessionList(
                        bridgeCard = bridgeCard,
                        sessions = sessions,
                        activeId = activeId,
                        name = name,
                        onNameChange = { name = it.take(80) },
                        onCreate = {
                            val id = Snoop.startSession(name)
                            if (id.isNotBlank()) {
                                name = ""
                                revision++
                                selectedSessionId = id
                            }
                        },
                        onOpen = { selectedSessionId = it.id },
                        onResume = {
                            if (Snoop.resumeSession(it.id)) revision++
                        },
                        onCopy = ::copySession,
                        onDelete = { pendingDelete = it },
                    )
                } else {
                    SessionDetail(
                        bridgeCard = bridgeCard,
                        session = selectedSession,
                        isActive = selectedSession.id == activeId,
                        onResume = {
                            if (Snoop.resumeSession(selectedSession.id)) revision++
                        },
                        onCloseSession = {
                            if (Snoop.closeActiveSession()) {
                                revision++
                                Toast.makeText(
                                    context,
                                    "Session closed",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                        exportInProgress = exportingSessionId == selectedSession.id,
                        onExport = { exportForAgent(selectedSession) },
                        downloadInProgress = downloadingSessionId == selectedSession.id,
                        onDownload = { downloadForAgent(selectedSession) },
                        onShareSelected = { shareSelectedPins(selectedSession, it) },
                        onSendSelectedToClaude = {
                            sendSelectedPinsToClaude(selectedSession, it)
                        },
                        onExportSelectedToIde = {
                            sendSelectedPinsToProject(selectedSession, it)
                        },
                        ideExportEnabled = bridgeState is ProjectBridgeUiState.Connected,
                        ideExportInProgress = bridgeState is ProjectBridgeUiState.Transferring,
                        onCopy = { copySession(selectedSession) },
                        onCopyItem = { copyItem(selectedSession.id, it) },
                        onCopyAttachment = { attachment, label ->
                            copyAttachment(selectedSession.id, attachment, label)
                        },
                        onDelete = { pendingDelete = selectedSession },
                        onPreview = { attachment, title ->
                            attachmentPreview = AttachmentPreview(
                                selectedSession.id,
                                attachment,
                                title,
                            )
                        },
                    )
                }
            }
        }
    }

    pendingDelete?.let { session ->
        val attachmentCount = session.items.sumOf { it.attachments.size }
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = NcColors.RowBg,
            titleContentColor = NcColors.Text,
            textContentColor = NcColors.Muted,
            title = { Text("Delete “${session.name}”?") },
            text = {
                Text(
                    "${session.items.size} feedback item and $attachmentCount attachment will be permanently removed.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val deleted = Snoop.deleteSession(session.id)
                        Toast.makeText(
                            context,
                            if (deleted) "Session deleted" else "Session could not be deleted",
                            Toast.LENGTH_SHORT,
                        ).show()
                        if (deleted) {
                            selectedSessionId = null
                            revision++
                        }
                        pendingDelete = null
                    },
                ) {
                    Text("DELETE SESSION", color = NcColors.ErrorText)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("CANCEL") }
            },
        )
    }

    attachmentPreview?.let { preview ->
        AttachmentPreviewDialog(preview = preview, onDismiss = { attachmentPreview = null })
    }
}

@Composable
private fun ProjectBridgeCard(
    state: ProjectBridgeUiState,
    packageName: String,
    sessionName: String?,
    onRetry: () -> Unit,
    onTransfer: () -> Unit,
) {
    val connected = when (state) {
        is ProjectBridgeUiState.Connected -> state
        is ProjectBridgeUiState.Transferring -> state.connection
        else -> null
    }
    val statusColor = when (state) {
        ProjectBridgeUiState.Checking,
        is ProjectBridgeUiState.Transferring,
        -> NcColors.Toggle
        is ProjectBridgeUiState.Connected -> {
            if (state.transferError == null) NcColors.StrGreen else NcColors.ErrorText
        }
        is ProjectBridgeUiState.Disconnected -> NcColors.ErrorText
    }
    val statusLabel = when (state) {
        ProjectBridgeUiState.Checking -> "CHECKING ADB + DAEMON"
        is ProjectBridgeUiState.Transferring -> "TRANSFERRING TO PROJECT"
        is ProjectBridgeUiState.Connected -> {
            if (state.transferError == null) "ADB + DAEMON CONNECTED" else "TRANSFER FAILED"
        }
        is ProjectBridgeUiState.Disconnected -> "ADB / DAEMON OFFLINE"
    }

    Surface(
        color = NcColors.RowBg,
        contentColor = NcColors.Text,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(statusColor, CircleShape)
                        .semantics {
                            contentDescription = statusLabel
                        },
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        statusLabel,
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        when {
                            // The name identifies the project; the full path wrapped to a
                            // second line and doubled the height of the card.
                            connected != null -> connected.projectName
                            state is ProjectBridgeUiState.Disconnected -> state.message
                            else -> "Testing the device-to-desktop route…"
                        },
                        color = NcColors.Muted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (
                    state == ProjectBridgeUiState.Checking ||
                    state is ProjectBridgeUiState.Transferring
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = statusColor,
                        strokeWidth = 2.dp,
                    )
                } else if (connected != null && sessionName != null) {
                    // Inline so the connected card stays a single row.
                    Button(
                        onClick = onTransfer,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 32.dp),
                    ) {
                        Icon(
                            Icons.Filled.UploadFile,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (connected.transferError == null) "SEND" else "RETRY",
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                    }
                }
            }

            when (state) {
                is ProjectBridgeUiState.Disconnected -> {
                    Text(
                        "snoop-mcp bridge --project-dir <project> --package $packageName",
                        color = NcColors.Muted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Button(
                        onClick = onRetry,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 32.dp),
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("RETRY", fontSize = 11.sp)
                    }
                }
                is ProjectBridgeUiState.Connected -> {
                    state.lastExportPath?.let {
                        Text(
                            "Transferred · $it",
                            color = NcColors.StrGreen,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    state.transferError?.let {
                        Text(
                            "$it · Check the daemon and try again.",
                            color = NcColors.ErrorText,
                            fontSize = 11.sp,
                        )
                    }
                }
                ProjectBridgeUiState.Checking,
                is ProjectBridgeUiState.Transferring,
                -> Unit
            }
        }
    }
}

@Composable
private fun SessionHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = NcColors.Text,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(subtitle, color = NcColors.Muted, fontSize = 12.sp)
        }
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Close sessions")
        }
    }
}

@Composable
private fun SessionList(
    bridgeCard: @Composable () -> Unit,
    sessions: List<FeedbackSession>,
    activeId: String?,
    name: String,
    onNameChange: (String) -> Unit,
    onCreate: () -> Unit,
    onOpen: (FeedbackSession) -> Unit,
    onResume: (FeedbackSession) -> Unit,
    onCopy: (FeedbackSession) -> Unit,
    onDelete: (FeedbackSession) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { bridgeCard() }
        item {
            Surface(
                color = NcColors.RowBg,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NcColors.Border),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "Start a focused review",
                        color = NcColors.Text,
                        fontWeight = FontWeight.Bold,
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = onNameChange,
                        label = { Text("Session name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Button(
                        onClick = onCreate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp),
                    ) {
                        Text("START NEW SESSION")
                    }
                }
            }
        }
        if (sessions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No feedback sessions yet.\nCreate one before capturing feedback.",
                        color = NcColors.Muted,
                    )
                }
            }
        } else {
            items(sessions, key = { it.id }) { session ->
                SessionCard(
                    session = session,
                    isActive = session.id == activeId,
                    onOpen = { onOpen(session) },
                    onResume = { onResume(session) },
                    onCopy = { onCopy(session) },
                    onDelete = { onDelete(session) },
                )
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: FeedbackSession,
    isActive: Boolean,
    onOpen: () -> Unit,
    onResume: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        onClick = onOpen,
        color = NcColors.RowBg,
        contentColor = NcColors.Text,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) NcColors.Ok else NcColors.Border,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isActive) {
                    Box(Modifier.size(10.dp).background(NcColors.Ok, CircleShape))
                }
                Text(
                    session.name,
                    modifier = Modifier.weight(1f),
                    color = NcColors.Text,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                SessionStatus(session.closedAt == null, isActive)
            }
            Text(
                "${session.items.size} feedback · Updated ${formatSessionDate(session.updatedAt)}",
                color = NcColors.Muted,
                fontSize = 12.sp,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (session.closedAt == null && !isActive) {
                    TextButton(onClick = onResume) { Text("MAKE ACTIVE") }
                }
                TextButton(onClick = onOpen) { Text("VIEW DETAILS") }
                TextButton(onClick = onCopy) { Text("COPY ALL") }
                TextButton(onClick = onDelete) {
                    Text("DELETE", color = NcColors.ErrorText)
                }
            }
        }
    }
}

@Composable
private fun SessionStatus(open: Boolean, active: Boolean) {
    val label = when {
        active -> "ACTIVE"
        open -> "OPEN"
        else -> "CLOSED"
    }
    val color = when {
        active -> NcColors.Ok
        open -> NcColors.Accent
        else -> NcColors.Border
    }
    Surface(color = color, contentColor = Color.White, shape = RoundedCornerShape(100.dp)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SessionDetail(
    bridgeCard: @Composable () -> Unit,
    session: FeedbackSession,
    isActive: Boolean,
    onResume: () -> Unit,
    onCloseSession: () -> Unit,
    exportInProgress: Boolean,
    onExport: () -> Unit,
    downloadInProgress: Boolean,
    onDownload: () -> Unit,
    onShareSelected: (Set<PinSelectionKey>) -> Unit,
    onSendSelectedToClaude: (Set<PinSelectionKey>) -> Unit,
    onExportSelectedToIde: (Set<PinSelectionKey>) -> Unit,
    ideExportEnabled: Boolean,
    ideExportInProgress: Boolean,
    onCopy: () -> Unit,
    onCopyItem: (FeedbackItem) -> Unit,
    onCopyAttachment: (FeedbackAttachment, String) -> Unit,
    onDelete: () -> Unit,
    onPreview: (FeedbackAttachment, String) -> Unit,
) {
    var selectedPins by remember(session.id) { mutableStateOf(emptySet<PinSelectionKey>()) }
    val claudeInstalled = ClaudeAppShare.isInstalled(LocalContext.current)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { bridgeCard() }
        item {
            Surface(
                color = NcColors.RowBg,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NcColors.Border),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SessionStatus(session.closedAt == null, isActive)
                        Text(
                            "Created ${formatSessionDate(session.createdAt)}",
                            color = NcColors.Muted,
                            fontSize = 12.sp,
                        )
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (session.closedAt == null && !isActive) {
                            Button(onClick = onResume) { Text("MAKE ACTIVE") }
                        }
                        if (isActive) {
                            TextButton(onClick = onCloseSession) { Text("CLOSE SESSION") }
                        }
                        Button(
                            onClick = onExport,
                            enabled = !exportInProgress,
                        ) {
                            if (exportInProgress) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Text("EXPORTING…")
                            } else {
                                Icon(
                                    Icons.Filled.FileDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text("EXPORT FOR AGENT")
                            }
                        }
                        TextButton(
                            onClick = onDownload,
                            enabled = !downloadInProgress,
                        ) {
                            if (downloadInProgress) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Text("SAVING…")
                            } else {
                                Icon(
                                    Icons.Filled.SaveAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text("SAVE ZIP")
                            }
                        }
                        Button(
                            onClick = { onSendSelectedToClaude(selectedPins) },
                            enabled = selectedPins.isNotEmpty(),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                if (selectedPins.isEmpty()) {
                                    "SEND TO CLAUDE"
                                } else {
                                    "SEND TO CLAUDE (${selectedPins.size})"
                                },
                            )
                        }
                        TextButton(
                            onClick = { onExportSelectedToIde(selectedPins) },
                            enabled = ideExportEnabled &&
                                !ideExportInProgress &&
                                selectedPins.isNotEmpty(),
                        ) {
                            if (ideExportInProgress) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Text("SENDING…")
                            } else {
                                Icon(
                                    Icons.Filled.UploadFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    if (selectedPins.isEmpty()) {
                                        "EXPORT TO IDE"
                                    } else {
                                        "EXPORT TO IDE (${selectedPins.size})"
                                    },
                                )
                            }
                        }
                        TextButton(
                            onClick = { onShareSelected(selectedPins) },
                            enabled = selectedPins.isNotEmpty(),
                        ) {
                            Icon(
                                Icons.Filled.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                if (selectedPins.isEmpty()) {
                                    "SHARE SELECTED"
                                } else {
                                    "SHARE SELECTED (${selectedPins.size})"
                                },
                            )
                        }
                        TextButton(onClick = onCopy) {
                            Icon(
                                Icons.Filled.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text("COPY ALL")
                        }
                        TextButton(onClick = onDelete) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text("DELETE", color = NcColors.ErrorText)
                        }
                    }
                    // Without this the greyed-out buttons read as "broken" rather than
                    // "nothing picked yet", which is the actual state.
                    Text(
                        text = when {
                            selectedPins.isEmpty() ->
                                "Tick “Include this pin in sharing” on a pin below to enable " +
                                    "Send to Claude, Export to IDE and Share."
                            !claudeInstalled ->
                                "${selectedPins.size} pin selected · Claude app not installed, " +
                                    "so Send to Claude opens the app chooser instead."
                            else -> "${selectedPins.size} pin selected."
                        },
                        color = NcColors.Muted,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        if (session.items.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No feedback saved in this session.", color = NcColors.Muted)
                }
            }
        } else {
            items(session.items.reversed(), key = { it.id }) { item ->
                FeedbackItemCard(
                    sessionId = session.id,
                    item = item,
                    selectedPinKeys = selectedPins,
                    onPinSelectionChange = { key, selected ->
                        selectedPins = if (selected) {
                            selectedPins + key
                        } else {
                            selectedPins - key
                        }
                    },
                    onCopy = { onCopyItem(item) },
                    onCopyAttachment = onCopyAttachment,
                    onPreview = onPreview,
                )
            }
        }
    }
}

@Composable
private fun FeedbackItemCard(
    sessionId: String,
    item: FeedbackItem,
    selectedPinKeys: Set<PinSelectionKey>,
    onPinSelectionChange: (PinSelectionKey, Boolean) -> Unit,
    onCopy: () -> Unit,
    onCopyAttachment: (FeedbackAttachment, String) -> Unit,
    onPreview: (FeedbackAttachment, String) -> Unit,
) {
    var technicalExpanded by remember(item.id) { mutableStateOf(false) }
    val screenshot = item.attachments.firstOrNull { it.type == "screenshot" }
    Surface(
        color = NcColors.RowBg,
        contentColor = NcColors.Text,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NcColors.Border),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryLabel(item)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        formatSessionDate(item.createdAt),
                        color = NcColors.Muted,
                        fontSize = 12.sp,
                    )
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = "Copy feedback item text and attachments",
                        )
                    }
                }
            }
            item.screenContext?.let { screen ->
                DetailBlock(
                    label = "SCREEN",
                    value = buildString {
                        append(screen.routeName.ifBlank { "Unknown route" })
                        if (screen.subScreen.isNotBlank()) append(" · ").append(screen.subScreen)
                        if (screen.screenFileHint.isNotBlank()) {
                            append("\n").append(screen.screenFileHint)
                        }
                    },
                )
            }
            if (item.note.isNotBlank()) {
                DetailBlock(label = "REVIEW NOTE", value = item.note)
            }
            screenshot?.let {
                AttachmentSectionHeader(
                    label = "SCREENSHOT",
                    onCopy = { onCopyAttachment(it, "Feedback screenshot") },
                )
                EvidenceImage(
                    sessionId = sessionId,
                    attachment = it,
                    pins = item.pins,
                    onClick = { onPreview(it, "Feedback screenshot") },
                )
            }
            if (item.pins.isNotEmpty()) {
                Text(
                    "PINS · ${item.pins.size}",
                    color = NcColors.Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                item.pins.forEach { pin ->
                    val selectionKey = PinSelectionKey(item.id, pin.id)
                    SavedPinCard(
                        sessionId = sessionId,
                        pin = pin,
                        item = item,
                        selected = selectionKey in selectedPinKeys,
                        onSelectionChange = {
                            onPinSelectionChange(selectionKey, it)
                        },
                        onCopyAttachment = onCopyAttachment,
                        onPreview = onPreview,
                    )
                }
            }
            TextButton(
                onClick = { technicalExpanded = !technicalExpanded },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) {
                Text(if (technicalExpanded) "HIDE TECHNICAL CONTEXT" else "SHOW TECHNICAL CONTEXT")
            }
            if (technicalExpanded) {
                TechnicalContext(item)
            }
        }
    }
}

@Composable
private fun CategoryLabel(item: FeedbackItem) {
    val color = Color(item.category.colorArgb)
    Surface(
        color = color,
        contentColor = readableContentColor(color),
        shape = RoundedCornerShape(100.dp),
    ) {
        Text(
            item.category.label.uppercase(),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun DetailBlock(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = NcColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(value, color = NcColors.Text, fontSize = 14.sp)
    }
}

@Composable
private fun EvidenceImage(
    sessionId: String,
    attachment: FeedbackAttachment,
    pins: List<FeedbackPin>,
    onClick: () -> Unit,
) {
    val bitmap = rememberAttachmentBitmap(sessionId, attachment.relativePath)
    if (bitmap == null) {
        Text("Attachment is unavailable.", color = NcColors.ErrorText, fontSize = 12.sp)
        return
    }
    val ratio = (bitmap.width.toFloat() / bitmap.height.toFloat()).coerceIn(0.35f, 2.2f)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .background(Color.Black, RoundedCornerShape(10.dp))
            .border(1.dp, NcColors.Border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Open feedback screenshot" },
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Feedback screenshot with ${pins.size} pins",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
        val markerRadius = with(LocalDensity.current) { 16.dp.roundToPx() }
        pins.forEach { pin ->
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (constraints.maxWidth * pin.xRatio).toInt() - markerRadius,
                            (constraints.maxHeight * pin.yRatio).toInt() - markerRadius,
                        )
                    }
                    .size(32.dp)
                    .background(Color(0xFFDC2626), CircleShape)
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    pin.id.toString(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun SavedPinCard(
    sessionId: String,
    pin: FeedbackPin,
    item: FeedbackItem,
    selected: Boolean,
    onSelectionChange: (Boolean) -> Unit,
    onCopyAttachment: (FeedbackAttachment, String) -> Unit,
    onPreview: (FeedbackAttachment, String) -> Unit,
) {
    Surface(
        color = NcColors.PanelBg,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NcColors.Border),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = onSelectionChange,
                    modifier = Modifier.size(48.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "#${pin.id} · ${pin.componentName.ifBlank { "Unnamed target" }}",
                        color = NcColors.Text,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (selected) "Selected for sharing" else "Include this pin in sharing",
                        color = if (selected) NcColors.Accent else NcColors.Muted,
                        fontSize = 11.sp,
                    )
                }
            }
            Text(
                "X ${pin.xRatio.toPercent()} · Y ${pin.yRatio.toPercent()}" +
                    pin.subScreen.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty(),
                color = NcColors.Muted,
                fontSize = 12.sp,
            )
            Text(
                pin.userNote.ifBlank { "No pin note entered." },
                color = if (pin.userNote.isBlank()) NcColors.Muted else NcColors.Text,
                fontSize = 13.sp,
            )
            if (pin.croppedAttachmentPaths.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    pin.croppedAttachmentPaths.forEachIndexed { index, path ->
                        val attachment = item.attachments.firstOrNull {
                            it.relativePath == path
                        } ?: return@forEachIndexed
                        RegionThumbnail(
                            sessionId = sessionId,
                            attachment = attachment,
                            label = "Photo ${index + 1}",
                            onCopy = {
                                onCopyAttachment(
                                    attachment,
                                    "Pin #${pin.id} · Photo ${index + 1}",
                                )
                            },
                            onClick = { onPreview(attachment, "Pin #${pin.id} · Photo ${index + 1}") },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RegionThumbnail(
    sessionId: String,
    attachment: FeedbackAttachment,
    label: String,
    onCopy: () -> Unit,
    onClick: () -> Unit,
) {
    val bitmap = rememberAttachmentBitmap(sessionId, attachment.relativePath)
    Column(
        modifier = Modifier.defaultMinSize(minWidth = 104.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (bitmap != null) {
            Box {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = label,
                    modifier = Modifier
                        .size(104.dp)
                        .background(NcColors.RowBg, RoundedCornerShape(10.dp))
                        .border(1.dp, NcColors.Border, RoundedCornerShape(10.dp))
                        .clickable(onClick = onClick)
                        .semantics { contentDescription = "Open $label" }
                        .padding(4.dp),
                    contentScale = ContentScale.Fit,
                )
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.68f), CircleShape),
                ) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = "Copy $label",
                        tint = Color.White,
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .background(NcColors.RowBg, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("Unavailable", color = NcColors.ErrorText, fontSize = 11.sp)
            }
        }
        Text(label, color = NcColors.Muted, fontSize = 11.sp)
    }
}

@Composable
private fun AttachmentSectionHeader(
    label: String,
    onCopy: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = NcColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        TextButton(
            onClick = onCopy,
            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
        ) {
            Icon(
                Icons.Filled.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text("COPY IMAGE")
        }
    }
}

@Composable
private fun TechnicalContext(item: FeedbackItem) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider(color = NcColors.Border)
        if (item.networkTransactionIds.isNotEmpty()) {
            DetailBlock(
                "NETWORK TRANSACTIONS",
                item.networkTransactionIds.joinToString { "#$it" },
            )
        }
        if (item.breadcrumbs.isNotEmpty()) {
            DetailBlock(
                "BREADCRUMBS",
                item.breadcrumbs.joinToString("\n") { "${it.type} · ${it.label}" },
            )
        }
        if (item.logs.isNotEmpty()) {
            DetailBlock("LOGS", item.logs.joinToString("\n"))
        }
        if (item.deviceInfo.isNotEmpty()) {
            DetailBlock(
                "DEVICE",
                item.deviceInfo.entries.joinToString("\n") { "${it.key}: ${it.value}" },
            )
        }
        item.semanticsSnapshot?.takeIf(String::isNotBlank)?.let {
            DetailBlock("ACCESSIBILITY SNAPSHOT", it)
        }
        item.stackTrace?.takeIf(String::isNotBlank)?.let {
            DetailBlock("STACK TRACE", it)
        }
        if (
            item.networkTransactionIds.isEmpty() &&
            item.breadcrumbs.isEmpty() &&
            item.logs.isEmpty() &&
            item.deviceInfo.isEmpty() &&
            item.semanticsSnapshot.isNullOrBlank() &&
            item.stackTrace.isNullOrBlank()
        ) {
            Text("No technical context was captured.", color = NcColors.Muted)
        }
    }
}

@Composable
private fun AttachmentPreviewDialog(
    preview: AttachmentPreview,
    onDismiss: () -> Unit,
) {
    val bitmap = rememberAttachmentBitmap(preview.sessionId, preview.attachment.relativePath)
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black,
            contentColor = Color.White,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = preview.title,
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Text(
                        "Attachment is unavailable.",
                        modifier = Modifier.align(Alignment.Center),
                        color = NcColors.ErrorText,
                    )
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(12.dp),
                    color = NcColors.PanelBg.copy(alpha = 0.96f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            preview.title,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            color = NcColors.Text,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(
                            onClick = {
                                FeedbackClipboard.copyAttachment(
                                    context,
                                    preview.sessionId,
                                    preview.attachment,
                                    preview.title,
                                )
                                Toast.makeText(
                                    context,
                                    "${preview.title} copied",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                Icons.Filled.ContentCopy,
                                contentDescription = "Copy ${preview.title}",
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Close image preview")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberAttachmentBitmap(sessionId: String, relativePath: String): Bitmap? =
    remember(sessionId, relativePath) {
        Snoop.feedbackAttachment(sessionId, relativePath)?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }

private fun formatSessionDate(timestamp: Long): String =
    SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun Float.toPercent(): String = "${(this * 100).toInt().coerceIn(0, 100)}%"
