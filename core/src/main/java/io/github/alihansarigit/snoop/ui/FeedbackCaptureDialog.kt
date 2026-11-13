package io.github.alihansarigit.snoop.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alihansarigit.snoop.Snoop
import io.github.alihansarigit.snoop.diagnostics.AnnotationStroke
import io.github.alihansarigit.snoop.diagnostics.AnnotationTool
import io.github.alihansarigit.snoop.diagnostics.UiTargetResolver
import io.github.alihansarigit.snoop.feedback.CapturedFeedbackPin
import io.github.alihansarigit.snoop.feedback.FeedbackCategory
import io.github.alihansarigit.snoop.feedback.FeedbackScreenContext
import io.github.alihansarigit.snoop.internal.OverlayState
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin

private enum class CaptureTool {
    SELECT,
    PEN,
    CIRCLE,
    ARROW,
}

private enum class CaptureExit {
    BACK,
    CLOSE,
}

private sealed interface CaptureHistory {
    data class Stroke(val value: AnnotationStroke) : CaptureHistory
    data class Pin(val id: Int) : CaptureHistory
}

private data class DraftRegion(
    val bitmap: Bitmap,
    val png: ByteArray,
)

private data class DraftPin(
    val id: Int,
    val xRatio: Float,
    val yRatio: Float,
    val componentName: String,
    val userNote: String,
    val timestamp: Long,
    val subScreen: String,
    val regions: List<DraftRegion> = emptyList(),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FeedbackCaptureOverlay(
    bitmap: Bitmap?,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    var note by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Snoop.feedbackCategories.first()) }
    var tool by remember { mutableStateOf(CaptureTool.SELECT) }
    val strokes = remember { mutableStateListOf<AnnotationStroke>() }
    val pins = remember { mutableStateListOf<DraftPin>() }
    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var selectionStart by remember { mutableStateOf<Offset?>(null) }
    var selectionCurrent by remember { mutableStateOf<Offset?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var pinDraft by remember { mutableStateOf<DraftPin?>(null) }
    var editingPinId by remember { mutableStateOf<Int?>(null) }
    var awaitingAdditionalPhoto by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showReview by remember { mutableStateOf(false) }
    var pendingExit by remember { mutableStateOf<CaptureExit?>(null) }
    var nextPinId by remember { mutableStateOf(1) }
    val history = remember { mutableStateListOf<CaptureHistory>() }
    val screenContext = OverlayState.captureScreenContext
    val draftAddsPin = awaitingAdditionalPhoto &&
        pinDraft != null &&
        pins.none { it.id == pinDraft?.id }
    val visiblePinCount = pins.size + if (draftAddsPin) 1 else 0
    val hasChanges = pinDraft != null ||
        pins.isNotEmpty() ||
        strokes.isNotEmpty() ||
        note.isNotBlank()
    val touchSlop = LocalViewConfiguration.current.touchSlop

    fun openSelection(start: Offset, end: Offset, includeCrop: Boolean) {
        val captured = bitmap ?: return
        if (canvasSize.width <= 0 || canvasSize.height <= 0) return
        val startRatio = start.normalized(canvasSize)
        val endRatio = end.normalized(canvasSize)
        val center = Offset(
            (startRatio.x + endRatio.x) / 2f,
            (startRatio.y + endRatio.y) / 2f,
        )
        val existing = pinDraft.takeIf { awaitingAdditionalPhoto }
        val region = when {
            includeCrop -> captured.crop(startRatio, endRatio)
            existing != null -> captured.crop(
                Offset(
                    (center.x - 0.08f).coerceIn(0f, 1f),
                    (center.y - 0.05f).coerceIn(0f, 1f),
                ),
                Offset(
                    (center.x + 0.08f).coerceIn(0f, 1f),
                    (center.y + 0.05f).coerceIn(0f, 1f),
                ),
            )
            else -> null
        }
        val componentName = existing?.componentName ?: UiTargetResolver.resolve(
            targets = OverlayState.captureTargets,
            xRatio = center.x,
            yRatio = center.y,
            screenshotWidth = captured.width,
            screenshotHeight = captured.height,
        ).orEmpty().ifBlank { "Pin #$nextPinId" }

        pinDraft = if (existing != null) {
            existing.copy(regions = existing.regions + listOfNotNull(region))
        } else {
            DraftPin(
                id = nextPinId,
                xRatio = center.x,
                yRatio = center.y,
                componentName = componentName,
                userNote = "",
                timestamp = System.currentTimeMillis(),
                subScreen = screenContext?.subScreen.orEmpty(),
                regions = listOfNotNull(region),
            )
        }
        awaitingAdditionalPhoto = false
        showPinDialog = true
    }

    fun requestExit(destination: CaptureExit) {
        if (hasChanges) {
            pendingExit = destination
        } else if (destination == CaptureExit.BACK) {
            onBack()
        } else {
            onClose()
        }
    }

    BackHandler(
        enabled = awaitingAdditionalPhoto && pendingExit == null,
    ) {
        awaitingAdditionalPhoto = false
        showPinDialog = pinDraft != null
    }

    BackHandler(
        enabled = !awaitingAdditionalPhoto &&
            !showPinDialog &&
            !showReview &&
            pendingExit == null,
    ) {
        requestExit(CaptureExit.BACK)
    }

    val gestureModifier = if (bitmap == null) {
        Modifier
    } else if (tool == CaptureTool.SELECT) {
        Modifier.pointerInput(canvasSize, awaitingAdditionalPhoto, touchSlop) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val start = down.position
                var current = start
                var dragged = false
                var pressed = true
                while (pressed) {
                    val change = awaitPointerEvent().changes.firstOrNull {
                        it.id == down.id
                    } ?: break
                    current = change.position
                    if (!dragged && (current - start).getDistance() >= touchSlop) {
                        dragged = true
                        selectionStart = start
                    }
                    if (dragged && change.positionChanged()) {
                        selectionCurrent = current
                        change.consume()
                    }
                    pressed = change.pressed
                }
                openSelection(start, current, includeCrop = dragged)
                selectionStart = null
                selectionCurrent = null
            }
        }
    } else {
        Modifier.pointerInput(tool, canvasSize) {
            detectDragGestures(
                onDragStart = { start ->
                    currentPoints = listOf(start.normalized(canvasSize))
                },
                onDrag = { change, _ ->
                    change.consume()
                    val point = change.position.normalized(canvasSize)
                    currentPoints = when (tool) {
                        CaptureTool.PEN -> currentPoints + point
                        CaptureTool.CIRCLE, CaptureTool.ARROW ->
                            listOf(currentPoints.firstOrNull() ?: point, point)
                        CaptureTool.SELECT -> currentPoints
                    }
                },
                onDragEnd = {
                    val annotationTool = tool.toAnnotationTool()
                    if (currentPoints.isNotEmpty() && annotationTool != null) {
                        val stroke = AnnotationStroke(annotationTool, currentPoints)
                        strokes += stroke
                        history += CaptureHistory.Stroke(stroke)
                    }
                    currentPoints = emptyList()
                },
                onDragCancel = { currentPoints = emptyList() },
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it },
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Captured screen",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
                alpha = 0.82f,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.11f)),
        )
        Box(modifier = Modifier.fillMaxSize().then(gestureModifier))
        AnnotationCanvas(
            strokes = strokes,
            current = currentPoints.takeIf { it.isNotEmpty() }
                ?.let { points -> tool.toAnnotationTool()?.let { AnnotationStroke(it, points) } },
            selectionStart = selectionStart,
            selectionCurrent = selectionCurrent,
        )
        val displayedPins = pins.toMutableList().apply {
            pinDraft?.takeIf { awaitingAdditionalPhoto }?.let { draft ->
                val index = indexOfFirst { it.id == draft.id }
                if (index >= 0) this[index] = draft else add(draft)
            }
        }
        displayedPins.forEach { pin ->
            PinMarker(
                pin = pin,
                canvasSize = canvasSize,
                onClick = {
                    pinDraft = pin
                    editingPinId = pins.firstOrNull { it.id == pin.id }?.id
                    awaitingAdditionalPhoto = false
                    showPinDialog = true
                },
            )
        }

        CaptureTopControls(
            pinCount = visiblePinCount,
            drawingCount = strokes.size,
            tool = tool,
            bitmapAvailable = bitmap != null,
            onBack = { requestExit(CaptureExit.BACK) },
            additionalPhotoPinId = pinDraft?.id?.takeIf { awaitingAdditionalPhoto },
            onCancelAdditionalPhoto = {
                awaitingAdditionalPhoto = false
                showPinDialog = pinDraft != null
            },
            onReview = { if (!awaitingAdditionalPhoto) showReview = true },
            onClose = { requestExit(CaptureExit.CLOSE) },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        CaptureToolRail(
            tool = tool,
            bitmapAvailable = bitmap != null,
            historyAvailable = history.isNotEmpty(),
            pinCount = visiblePinCount,
            additionalPhotoActive = awaitingAdditionalPhoto,
            onToolSelected = { if (!awaitingAdditionalPhoto) tool = it },
            onUndo = {
                val action = history.lastOrNull()
                if (action != null) history.removeAt(history.lastIndex)
                when (action) {
                    is CaptureHistory.Stroke -> strokes.remove(action.value)
                    is CaptureHistory.Pin -> pins.removeAll { it.id == action.id }
                    null -> Unit
                }
            },
            onReview = { if (!awaitingAdditionalPhoto) showReview = true },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (showPinDialog) {
        pinDraft?.let { draft ->
            PinNoteSheet(
                draft = draft,
                onAddPhoto = { componentName, pinNote ->
                    pinDraft = draft.copy(
                        componentName = componentName,
                        userNote = pinNote,
                    )
                    awaitingAdditionalPhoto = true
                    showPinDialog = false
                    tool = CaptureTool.SELECT
                },
                onRemovePhoto = { index, componentName, pinNote ->
                    pinDraft = draft.copy(
                        componentName = componentName,
                        userNote = pinNote,
                        regions = draft.regions.filterIndexed { photoIndex, _ ->
                            photoIndex != index
                        },
                    )
                },
                onConfirm = { componentName, pinNote ->
                    val saved = draft.copy(
                        componentName = componentName.ifBlank { "Pin #${draft.id}" },
                        userNote = pinNote,
                    )
                    val existingIndex = pins.indexOfFirst { it.id == editingPinId }
                    if (existingIndex >= 0) {
                        pins[existingIndex] = saved
                    } else {
                        pins += saved
                        history += CaptureHistory.Pin(saved.id)
                        nextPinId = maxOf(nextPinId, saved.id + 1)
                    }
                    pinDraft = null
                    editingPinId = null
                    awaitingAdditionalPhoto = false
                    showPinDialog = false
                },
                onDismiss = {
                    pinDraft = null
                    editingPinId = null
                    awaitingAdditionalPhoto = false
                    showPinDialog = false
                },
            )
        }
    }

    if (showReview) {
        CaptureReviewSheet(
            pins = pins,
            drawingCount = strokes.size,
            screenContext = screenContext,
            category = category,
            onCategoryChange = { category = it },
            note = note,
            onNoteChange = { note = it },
            onEdit = { pin ->
                showReview = false
                pinDraft = pin
                editingPinId = pin.id
                showPinDialog = true
            },
            onDelete = { pin ->
                pins.remove(pin)
                history.removeAll { it is CaptureHistory.Pin && it.id == pin.id }
            },
            onSave = { sessionId ->
                Snoop.saveCapturedFeedback(
                    sessionId = sessionId,
                    note = note,
                    category = category,
                    strokes = strokes.toList(),
                    pins = pins.map(DraftPin::toCapturedPin),
                )
            },
            onDismiss = { showReview = false },
        )
    }

    pendingExit?.let { exit ->
        AlertDialog(
            onDismissRequest = { pendingExit = null },
            title = { Text("Discard feedback draft?") },
            text = { Text("Pins, drawings and notes from this capture will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingExit = null
                    if (exit == CaptureExit.BACK) onBack() else onClose()
                }) {
                    Text("DISCARD", color = NcColors.ErrorText)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingExit = null }) { Text("KEEP EDITING") }
            },
        )
    }
}

@Composable
private fun CaptureTopControls(
    pinCount: Int,
    drawingCount: Int,
    tool: CaptureTool,
    bitmapAvailable: Boolean,
    additionalPhotoPinId: Int?,
    onBack: () -> Unit,
    onCancelAdditionalPhoto: () -> Unit,
    onReview: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            color = NcColors.PanelBg.copy(alpha = 0.96f),
            contentColor = NcColors.Text,
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 8.dp,
        ) {
            Box {
                PointerShield(Modifier.matchParentSize())
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Debug Hub",
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Capture Feedback",
                            color = NcColors.Text,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "$pinCount pins · $drawingCount drawings",
                            color = NcColors.Muted,
                            fontSize = 12.sp,
                        )
                    }
                    TextButton(
                        onClick = onReview,
                        enabled = additionalPhotoPinId == null,
                    ) {
                        Text("NEXT")
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close capture")
                    }
                }
            }
        }

        Surface(
            color = if (bitmapAvailable) {
                NcColors.PanelBg.copy(alpha = 0.88f)
            } else {
                NcColors.Danger.copy(alpha = 0.94f)
            },
            shape = RoundedCornerShape(100.dp),
        ) {
            Box {
                PointerShield(Modifier.matchParentSize())
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = if (!bitmapAvailable) {
                            "Screenshot unavailable · annotation is disabled"
                        } else if (additionalPhotoPinId != null) {
                            "Pin #$additionalPhotoPinId · Tap or drag to capture a photo"
                        } else {
                            when (tool) {
                                CaptureTool.SELECT -> "Tap to pin · drag to capture an area"
                                CaptureTool.PEN -> "Draw freely on the screen"
                                CaptureTool.CIRCLE -> "Drag around the area to highlight"
                                CaptureTool.ARROW -> "Drag from start to destination"
                            }
                        },
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(
                            start = 14.dp,
                            top = 8.dp,
                            bottom = 8.dp,
                        ),
                    )
                    if (additionalPhotoPinId != null) {
                        TextButton(onClick = onCancelAdditionalPhoto) {
                            Text("CANCEL", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptureToolRail(
    tool: CaptureTool,
    bitmapAvailable: Boolean,
    historyAvailable: Boolean,
    pinCount: Int,
    additionalPhotoActive: Boolean,
    onToolSelected: (CaptureTool) -> Unit,
    onUndo: () -> Unit,
    onReview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        color = NcColors.PanelBg.copy(alpha = 0.97f),
        shadowElevation = 12.dp,
    ) {
        Box {
            PointerShield(Modifier.matchParentSize())
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CaptureToolButton(
                    icon = Icons.Filled.LocationOn,
                    label = "PIN",
                    selected = tool == CaptureTool.SELECT,
                    enabled = bitmapAvailable && !additionalPhotoActive,
                ) { onToolSelected(CaptureTool.SELECT) }
                CaptureToolButton(
                    icon = Icons.Filled.Edit,
                    label = "PEN",
                    selected = tool == CaptureTool.PEN,
                    enabled = bitmapAvailable && !additionalPhotoActive,
                ) { onToolSelected(CaptureTool.PEN) }
                CaptureToolButton(
                    icon = Icons.Filled.RadioButtonUnchecked,
                    label = "CIRCLE",
                    selected = tool == CaptureTool.CIRCLE,
                    enabled = bitmapAvailable && !additionalPhotoActive,
                ) { onToolSelected(CaptureTool.CIRCLE) }
                CaptureToolButton(
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    label = "ARROW",
                    selected = tool == CaptureTool.ARROW,
                    enabled = bitmapAvailable && !additionalPhotoActive,
                ) { onToolSelected(CaptureTool.ARROW) }
                CaptureToolButton(
                    icon = Icons.AutoMirrored.Filled.Undo,
                    label = "UNDO",
                    selected = false,
                    enabled = historyAvailable && !additionalPhotoActive,
                    onClick = onUndo,
                )
                CaptureToolButton(
                    icon = Icons.Filled.Checklist,
                    label = if (pinCount == 0) "REVIEW" else "REVIEW $pinCount",
                    selected = false,
                    enabled = !additionalPhotoActive,
                    onClick = onReview,
                )
            }
        }
    }
}

@Composable
private fun CaptureToolButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val containerColor = if (selected) NcColors.Accent else NcColors.RowBg
    val contentColor = if (selected) Color.White else NcColors.Text
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.defaultMinSize(minHeight = 52.dp, minWidth = 72.dp),
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NcColors.Border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PointerShield(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(onTap = {})
        },
    )
}

@Composable
private fun AnnotationCanvas(
    strokes: List<AnnotationStroke>,
    current: AnnotationStroke?,
    selectionStart: Offset?,
    selectionCurrent: Offset?,
) {
    Canvas(Modifier.fillMaxSize()) {
        (strokes + listOfNotNull(current)).forEach { stroke ->
            val points = stroke.points.map { Offset(it.x * size.width, it.y * size.height) }
            if (points.isEmpty()) return@forEach
            when (stroke.tool) {
                AnnotationTool.PEN -> {
                    val path = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(path, Color(0xFFEF4444), style = Stroke(width = 7f))
                }
                AnnotationTool.CIRCLE -> {
                    val first = points.first()
                    val last = points.last()
                    drawOval(
                        color = Color(0xFFEF4444),
                        topLeft = Offset(minOf(first.x, last.x), minOf(first.y, last.y)),
                        size = Size(abs(last.x - first.x), abs(last.y - first.y)),
                        style = Stroke(width = 7f),
                    )
                }
                AnnotationTool.ARROW -> drawArrow(points.first(), points.last())
            }
        }

        if (selectionStart != null && selectionCurrent != null) {
            val left = minOf(selectionStart.x, selectionCurrent.x)
            val top = minOf(selectionStart.y, selectionCurrent.y)
            val width = abs(selectionCurrent.x - selectionStart.x)
            val height = abs(selectionCurrent.y - selectionStart.y)
            drawRect(
                color = Color.Yellow.copy(alpha = 0.18f),
                topLeft = Offset(left, top),
                size = Size(width, height),
            )
            drawRect(
                color = Color.Yellow,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(width = 5f),
            )
        }
    }
}

@Composable
private fun PinMarker(
    pin: DraftPin,
    canvasSize: IntSize,
    onClick: () -> Unit,
) {
    val touchRadiusPx = with(LocalDensity.current) { 24.dp.roundToPx() }
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (pin.xRatio * canvasSize.width).toInt() - touchRadiusPx,
                    (pin.yRatio * canvasSize.height).toInt() - touchRadiusPx,
                )
            }
            .size(48.dp)
            .semantics {
                contentDescription = "Edit pin ${pin.id}: ${pin.componentName}"
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(Color(0xFFDC2626), CircleShape)
                .border(2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                pin.id.toString(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinNoteSheet(
    draft: DraftPin,
    onAddPhoto: (String, String) -> Unit,
    onRemovePhoto: (Int, String, String) -> Unit,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var componentName by remember(draft.id) { mutableStateOf(draft.componentName) }
    var note by remember(draft.id) { mutableStateOf(draft.userNote) }
    val launchVoiceInput = rememberVoiceInput { spoken ->
        note = listOf(note.trim(), spoken.trim()).filter(String::isNotEmpty).joinToString(" ")
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NcColors.RowBg,
        contentColor = NcColors.Text,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedButton(
                onClick = { onAddPhoto(componentName, note) },
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp),
            ) {
                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "ADD PHOTO",
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Pin #${draft.id}",
                    color = NcColors.Text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
                Text(
                    "Name the target and describe the expected change.",
                    color = NcColors.Muted,
                    fontSize = 13.sp,
                )
            }
            if (draft.regions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    draft.regions.forEachIndexed { index, photo ->
                        Box {
                            Image(
                                bitmap = photo.bitmap.asImageBitmap(),
                                contentDescription = "Attached photo ${index + 1}",
                                modifier = Modifier
                                    .size(104.dp)
                                    .background(NcColors.PanelBg, RoundedCornerShape(10.dp))
                                    .border(1.dp, NcColors.Border, RoundedCornerShape(10.dp))
                                    .padding(4.dp),
                                contentScale = ContentScale.Fit,
                            )
                            IconButton(
                                onClick = {
                                    onRemovePhoto(index, componentName, note)
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(48.dp)
                                    .background(Color.Black.copy(alpha = 0.62f), CircleShape),
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Remove photo ${index + 1}",
                                    tint = Color.White,
                                )
                            }
                        }
                    }
                }
            }
            OutlinedTextField(
                value = componentName,
                onValueChange = { componentName = it.take(160) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Target component") },
                supportingText = { Text("Auto-detected; edit it if needed.") },
                singleLine = true,
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(4_000) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("What should change?") },
                placeholder = { Text("Describe the issue and the expected result") },
                minLines = 3,
                maxLines = 6,
            )
            TextButton(onClick = launchVoiceInput) { Text("VOICE NOTE") }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) { Text("CANCEL") }
                Button(
                    onClick = { onConfirm(componentName, note) },
                    enabled = componentName.isNotBlank() || note.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NcColors.Accent,
                        contentColor = Color.White,
                    ),
                ) {
                    Text("SAVE PIN")
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CaptureReviewSheet(
    pins: List<DraftPin>,
    drawingCount: Int,
    screenContext: FeedbackScreenContext?,
    category: FeedbackCategory,
    onCategoryChange: (FeedbackCategory) -> Unit,
    note: String,
    onNoteChange: (String) -> Unit,
    onEdit: (DraftPin) -> Unit,
    onDelete: (DraftPin) -> Unit,
    onSave: (String?) -> Boolean,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var addingCategory by remember { mutableStateOf(false) }
    var newCategory by remember { mutableStateOf("") }
    var sessionRevision by remember { mutableStateOf(0) }
    var sessionPickerExpanded by remember { mutableStateOf(false) }
    var newSessionName by remember { mutableStateOf("") }
    val openSessions = remember(sessionRevision) {
        Snoop.feedbackSessionSnapshot().filter { it.closedAt == null }
    }
    var selectedSessionId by remember {
        mutableStateOf(Snoop.activeSessionId() ?: openSessions.firstOrNull()?.id)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NcColors.RowBg,
        contentColor = NcColors.Text,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Review feedback",
                    color = NcColors.Text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${pins.size} pins · $drawingCount drawings",
                    color = NcColors.Muted,
                    fontSize = 13.sp,
                )
            }

            ReviewSessionPicker(
                sessions = openSessions,
                selectedSessionId = selectedSessionId,
                expanded = sessionPickerExpanded,
                newSessionName = newSessionName,
                onExpandedChange = { sessionPickerExpanded = it },
                onSelect = {
                    selectedSessionId = it
                    sessionPickerExpanded = false
                },
                onNewSessionNameChange = { newSessionName = it.take(80) },
                onCreate = {
                    val id = Snoop.startSession(newSessionName)
                    if (id.isNotBlank()) {
                        newSessionName = ""
                        sessionRevision++
                        selectedSessionId = id
                        sessionPickerExpanded = false
                    }
                },
            )

            if (pins.isNotEmpty()) {
                ReviewSectionTitle("PINNED TASKS")
                pins.forEach { pin ->
                    ReviewPinCard(pin = pin, onEdit = onEdit, onDelete = onDelete)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ReviewSectionTitle("CATEGORY")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Snoop.feedbackCategories.forEach { option ->
                        CategoryChip(
                            category = option,
                            selected = category.id == option.id,
                            onClick = { onCategoryChange(option) },
                        )
                    }
                    Surface(
                        onClick = { addingCategory = !addingCategory },
                        color = NcColors.PanelBg,
                        contentColor = NcColors.Text,
                        shape = RoundedCornerShape(100.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NcColors.Border),
                    ) {
                        Text(
                            "+ CUSTOM",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                if (addingCategory) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = newCategory,
                            onValueChange = { newCategory = it.take(40) },
                            modifier = Modifier.weight(1f),
                            label = { Text("Custom category") },
                            singleLine = true,
                        )
                        Button(
                            onClick = {
                                val id = Snoop.addFeedbackCategory(
                                    newCategory.trim(),
                                    0xFFF97316.toInt(),
                                )
                                Snoop.feedbackCategories.firstOrNull { it.id == id }
                                    ?.let(onCategoryChange)
                                newCategory = ""
                                addingCategory = false
                            },
                            enabled = newCategory.isNotBlank(),
                        ) {
                            Text("ADD")
                        }
                    }
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { onNoteChange(it.take(8_000)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Overall context") },
                placeholder = { Text("Add context that applies to the whole screen") },
                minLines = 3,
                maxLines = 7,
            )

            HorizontalDivider(color = NcColors.Border)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(formatDraftPrompt(screenContext, pins)))
                        Toast.makeText(context, "Markdown copied", Toast.LENGTH_SHORT).show()
                    },
                    enabled = pins.isNotEmpty(),
                ) {
                    Text("COPY MARKDOWN")
                }
                Button(
                    onClick = {
                        val saved = onSave(selectedSessionId)
                        Toast.makeText(
                            context,
                            if (saved) {
                                "Feedback saved to selected session"
                            } else {
                                "Select an open session before saving"
                            },
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                    enabled = selectedSessionId != null &&
                        (pins.isNotEmpty() || drawingCount > 0 || note.isNotBlank()),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NcColors.Accent,
                        contentColor = Color.White,
                    ),
                ) {
                    Text("SAVE FEEDBACK")
                }
            }
        }
    }
}

@Composable
private fun ReviewSessionPicker(
    sessions: List<io.github.alihansarigit.snoop.feedback.FeedbackSession>,
    selectedSessionId: String?,
    expanded: Boolean,
    newSessionName: String,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
    onNewSessionNameChange: (String) -> Unit,
    onCreate: () -> Unit,
) {
    val selected = sessions.firstOrNull { it.id == selectedSessionId }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ReviewSectionTitle("SAVE TO SESSION")
        Surface(
            onClick = { onExpandedChange(!expanded) },
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp),
            color = NcColors.PanelBg,
            contentColor = NcColors.Text,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (selected == null) NcColors.Pending else NcColors.Accent,
            ),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    selected?.name ?: "Choose a session",
                    color = NcColors.Text,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (selected == null) {
                        "Feedback cannot be saved until an open session is selected."
                    } else {
                        "${selected.items.size} existing feedback · Tap to change"
                    },
                    color = NcColors.Muted,
                    fontSize = 12.sp,
                )
            }
        }
        if (expanded) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = NcColors.PanelBg,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NcColors.Border),
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (sessions.isEmpty()) {
                        Text("No open sessions.", color = NcColors.Muted)
                    } else {
                        sessions.forEach { session ->
                            Surface(
                                onClick = { onSelect(session.id) },
                                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                                color = if (session.id == selectedSessionId) {
                                    NcColors.Accent
                                } else {
                                    NcColors.RowBg
                                },
                                contentColor = Color.White,
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        session.name,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text("${session.items.size}", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = NcColors.Border)
                    OutlinedTextField(
                        value = newSessionName,
                        onValueChange = onNewSessionNameChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("New session name") },
                        singleLine = true,
                    )
                    Button(
                        onClick = onCreate,
                        enabled = newSessionName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                    ) {
                        Text("CREATE & SELECT SESSION")
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewSectionTitle(text: String) {
    Text(
        text = text,
        color = NcColors.Muted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun ReviewPinCard(
    pin: DraftPin,
    onEdit: (DraftPin) -> Unit,
    onDelete: (DraftPin) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = NcColors.PanelBg,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NcColors.Border),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "#${pin.id} · ${pin.componentName}",
                color = NcColors.Text,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (pin.regions.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    pin.regions.forEachIndexed { index, region ->
                        Image(
                            bitmap = region.bitmap.asImageBitmap(),
                            contentDescription = "Pin ${pin.id} photo ${index + 1}",
                            modifier = Modifier
                                .size(72.dp)
                                .background(NcColors.RowBg, RoundedCornerShape(8.dp))
                                .padding(4.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }
            Text(
                pin.userNote.ifBlank { "No note added." },
                color = if (pin.userNote.isBlank()) NcColors.Muted else NcColors.Text,
                fontSize = 13.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onEdit(pin) }) { Text("EDIT") }
                TextButton(onClick = { onDelete(pin) }) {
                    Text("DELETE", color = NcColors.ErrorText)
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(
    category: FeedbackCategory,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val categoryColor = Color(category.colorArgb)
    val background = if (selected) categoryColor else NcColors.PanelBg
    val foreground = if (selected) readableContentColor(categoryColor) else NcColors.Text
    Surface(
        onClick = onClick,
        color = background,
        contentColor = foreground,
        shape = RoundedCornerShape(100.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) categoryColor else NcColors.Border,
        ),
    ) {
        Text(
            category.label.uppercase(),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrow(start: Offset, end: Offset) {
    drawLine(Color(0xFFEF4444), start, end, strokeWidth = 7f)
    val angle = atan2(end.y - start.y, end.x - start.x)
    val length = 34f
    val spread = Math.toRadians(28.0).toFloat()
    drawLine(
        Color(0xFFEF4444),
        end,
        Offset(end.x - length * cos(angle - spread), end.y - length * sin(angle - spread)),
        strokeWidth = 7f,
    )
    drawLine(
        Color(0xFFEF4444),
        end,
        Offset(end.x - length * cos(angle + spread), end.y - length * sin(angle + spread)),
        strokeWidth = 7f,
    )
}

private fun CaptureTool.toAnnotationTool(): AnnotationTool? = when (this) {
    CaptureTool.SELECT -> null
    CaptureTool.PEN -> AnnotationTool.PEN
    CaptureTool.CIRCLE -> AnnotationTool.CIRCLE
    CaptureTool.ARROW -> AnnotationTool.ARROW
}

private fun Offset.normalized(size: IntSize): Offset {
    if (size.width <= 0 || size.height <= 0) return Offset.Zero
    return Offset(
        x = (x / size.width).coerceIn(0f, 1f),
        y = (y / size.height).coerceIn(0f, 1f),
    )
}

private fun Bitmap.crop(start: Offset, end: Offset): DraftRegion? {
    val left = (minOf(start.x, end.x) * width).toInt().coerceIn(0, width)
    val top = (minOf(start.y, end.y) * height).toInt().coerceIn(0, height)
    val right = (maxOf(start.x, end.x) * width).toInt().coerceIn(left, width)
    val bottom = (maxOf(start.y, end.y) * height).toInt().coerceIn(top, height)
    if (right - left < 2 || bottom - top < 2) return null
    val cropped = Bitmap.createBitmap(this, left, top, right - left, bottom - top)
        .copy(Bitmap.Config.ARGB_8888, false)
    val png = ByteArrayOutputStream().use { output ->
        cropped.compress(Bitmap.CompressFormat.PNG, 100, output)
        output.toByteArray()
    }
    return DraftRegion(cropped, png)
}

private fun DraftPin.toCapturedPin(): CapturedFeedbackPin = CapturedFeedbackPin(
    id = id,
    xRatio = xRatio,
    yRatio = yRatio,
    componentName = componentName,
    userNote = userNote,
    timestamp = timestamp,
    subScreen = subScreen,
    croppedRegionPng = regions.map(DraftRegion::png),
)

private fun formatDraftPrompt(
    context: FeedbackScreenContext?,
    pins: List<DraftPin>,
): String = pins.joinToString("\n\n---\n\n") { pin ->
    buildString {
        appendLine("### Agent Task Request")
        appendLine()
        context?.let {
            appendLine("**Platform:** ${it.platformName}")
            appendLine("**Screen Route:** `${it.routeName}`")
            if (it.screenFileHint.isNotBlank()) appendLine("**Screen File:** `${it.screenFileHint}`")
        }
        if (pin.subScreen.isNotBlank()) appendLine("**Tab/Sub-screen:** `${pin.subScreen}`")
        appendLine("**Target Component:** `${pin.componentName}`")
        appendLine()
        appendLine("**User Voice/Text Note:**")
        appendLine("> \"${pin.userNote}\"")
        appendLine()
        appendLine("**Context Metadata:**")
        appendLine("- Pin ID: #${pin.id}")
        appendLine(
            "- Coordinates Ratio: X:${pin.xRatio.toRatioString()}, Y:${pin.yRatio.toRatioString()}",
        )
        append("- Captured At: ${pin.timestamp}")
    }
}

private fun Float.toRatioString(): String = (round(this * 1_000f) / 1_000f).toString()
