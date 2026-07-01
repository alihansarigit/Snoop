package io.github.alihansarigit.snoop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures

/** PomoPet debug overlay palette. */
internal object NcColors {
    val PanelBg = Color(0xFF111827)
    val RowBg = Color(0xFF1F2937)
    val Border = Color(0xFF374151)
    val Text = Color(0xFFE5E7EB)
    val Muted = Color(0xFF9CA3AF)
    val Accent = Color(0xFF2563EB)
    val Ok = Color(0xFF16A34A)
    val Err = Color(0xFFB91C1C)
    val Pending = Color(0xFFF59E0B)
    val Toggle = Color(0xFFFBBF24)
    val KeyBlue = Color(0xFF93C5FD)
    val StrGreen = Color(0xFF6EE7B7)
    val NumRed = Color(0xFFFCA5A5)
    val Bubble = Color(0xFF1F2937)
    val Danger = Color(0xFFB91C1C)

    // Per-button toolbar accents. Each button owns a distinct hue; selected/pressed
    // shows it at full opacity, unselected/idle at 30% (see NcChip / NcButton idleDim).
    val FilterAll = Color(0xFF3B82F6)   // blue
    val FilterErr = Color(0xFFEF4444)   // red
    val FilterPend = Color(0xFFD97706)  // amber
    val ActionClear = Color(0xFF14B8A6) // teal
    val ActionCopyAll = Color(0xFF8B5CF6) // violet
    val ActionHide = Color(0xFFEC4899)  // pink
    val ActionClose = Color(0xFF64748B) // slate

    // Per-request row buttons (LogRow): REQ/RES toggles + copy actions.
    val ToggleReq = Color(0xFF6366F1)     // indigo
    val ToggleRes = Color(0xFF059669)     // emerald
    val CopyEndpoint = Color(0xFFA855F7)  // purple
    val CopyFull = Color(0xFFF97316)      // orange
    val CopyRequest = Color(0xFF06B6D4)   // cyan
    val CopyResponse = Color(0xFF84CC16)  // lime
    val CopyCurl = Color(0xFFEAB308)      // yellow
}

/** Opacity applied to a toolbar button's fill when it is not selected / not pressed. */
private const val IDLE_ALPHA = 0.3f

@Composable
internal fun NcText(
    text: String,
    size: TextUnit = 12.sp,
    color: Color = NcColors.Text,
    weight: FontWeight = FontWeight.Normal,
) {
    Text(
        text = text,
        style = TextStyle(
            fontSize = size,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontWeight = weight,
        ),
    )
}

/** [NcText] overload for rich, multi-colour lines (e.g. a coloured `Header: value`). */
@Composable
internal fun NcText(
    text: AnnotatedString,
    size: TextUnit = 12.sp,
    color: Color = NcColors.Text,
    weight: FontWeight = FontWeight.Normal,
) {
    Text(
        text = text,
        style = TextStyle(
            fontSize = size,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontWeight = weight,
        ),
    )
}

/**
 * @param idleDim when true, the button owns [bg] as its accent colour and renders it
 *   at [IDLE_ALPHA] while idle, snapping to full opacity while pressed. When false the
 *   fill is a solid [bg] (used by the per-row copy buttons).
 */
@Composable
internal fun NcButton(
    label: String,
    bg: Color = NcColors.Border,
    idleDim: Boolean = false,
    onClick: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val fill = if (idleDim) bg.copy(alpha = if (pressed) 1f else IDLE_ALPHA) else bg
    Box(
        modifier = Modifier
            .background(fill, RoundedCornerShape(4.dp))
            .pointerInput(label) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onClick() },
                )
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        NcText(label, size = 10.sp, weight = FontWeight.Bold)
    }
}

/** A two-state toggle (e.g. REQ/RES): [color] at full opacity when open, [IDLE_ALPHA] when collapsed. */
@Composable
internal fun NcToggle(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = if (selected) 1f else IDLE_ALPHA), RoundedCornerShape(4.dp))
            .pointerInput(label) { detectTapGestures(onTap = { onClick() }) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        NcText(label, size = 10.sp, weight = FontWeight.Bold)
    }
}

@Composable
internal fun NcChip(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = if (selected) 1f else IDLE_ALPHA), RoundedCornerShape(12.dp))
            .pointerInput(label) { detectTapGestures(onTap = { onClick() }) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        NcText(label, size = 10.sp, weight = FontWeight.Bold)
    }
}

@Composable
internal fun NcBadge(count: Int, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
            .shadow(4.dp, CircleShape)
            .background(color, CircleShape)
            .border(1.5.dp, Color.White, CircleShape)
            .padding(horizontal = 4.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        NcText(
            text = if (count > 999) "999+" else count.toString(),
            size = 10.sp,
            color = Color.White,
            weight = FontWeight.Bold,
        )
    }
}

@Composable
internal fun NcSearch(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NcColors.RowBg, RoundedCornerShape(6.dp))
            .border(1.dp, NcColors.Border, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                NcText(placeholder, size = 11.sp, color = Color(0xFF6B7280))
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                cursorBrush = SolidColor(NcColors.Text),
                textStyle = TextStyle(
                    fontSize = 11.sp,
                    color = NcColors.Text,
                    fontFamily = FontFamily.Monospace,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .pointerInput(Unit) { detectTapGestures(onTap = { onValueChange("") }) }
                    .padding(start = 8.dp),
            ) {
                NcText("✕", size = 12.sp, color = NcColors.Muted)
            }
        }
    }
}
