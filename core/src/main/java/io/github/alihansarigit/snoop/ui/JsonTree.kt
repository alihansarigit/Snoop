package io.github.alihansarigit.snoop.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

/** Render a body as a collapsible JSON tree, or as raw text if it isn't JSON. */
@Composable
internal fun JsonBody(body: String?, emptyTint: androidx.compose.ui.graphics.Color, emptyText: String) {
    if (body.isNullOrBlank()) {
        NcText(emptyText, size = 10.sp, color = emptyTint)
        return
    }
    val root = remember(body) { parseJsonOrNull(body) }
    if (root == null) {
        NcText(body, size = 10.sp, color = NcColors.StrGreen)
    } else {
        JsonNode(label = null, value = root, depth = 0, initiallyExpanded = true)
    }
}

private fun parseJsonOrNull(body: String): Any? = runCatching {
    when (body.trim().firstOrNull()) {
        '{' -> JSONObject(body)
        '[' -> JSONArray(body)
        else -> null
    }
}.getOrNull()

@Composable
private fun JsonNode(label: String?, value: Any?, depth: Int, initiallyExpanded: Boolean = false) {
    val indent = (depth * 12).dp
    when (value) {
        is JSONObject -> CollapsibleBranch(
            indent = indent,
            label = label,
            open = "{",
            close = "}",
            childCount = value.length(),
            initiallyExpanded = initiallyExpanded,
        ) {
            val keys = value.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                JsonNode(label = k, value = value.opt(k), depth = depth + 1)
            }
        }
        is JSONArray -> CollapsibleBranch(
            indent = indent,
            label = label,
            open = "[",
            close = "]",
            childCount = value.length(),
            initiallyExpanded = initiallyExpanded,
        ) {
            for (i in 0 until value.length()) {
                JsonNode(label = "[$i]", value = value.opt(i), depth = depth + 1)
            }
        }
        else -> LeafLine(indent = indent, label = label, value = value)
    }
}

@Composable
private fun CollapsibleBranch(
    indent: Dp,
    label: String?,
    open: String,
    close: String,
    childCount: Int,
    initiallyExpanded: Boolean,
    renderChildren: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent, top = 1.dp, bottom = 1.dp)
            .pointerInput(Unit) { detectTapGestures(onTap = { expanded = !expanded }) },
        verticalAlignment = Alignment.Top,
    ) {
        NcText(
            text = if (expanded) "[-] " else "[+] ",
            size = 10.sp,
            color = NcColors.Toggle,
            weight = FontWeight.Bold,
        )
        val keyPart = if (label != null) "\"$label\": " else ""
        NcText(
            text = if (expanded) "$keyPart$open" else "$keyPart$open … $close ($childCount)",
            size = 10.sp,
            color = NcColors.Text,
        )
    }
    if (expanded) {
        renderChildren()
        Row(modifier = Modifier.padding(start = indent)) {
            NcText(text = close, size = 10.sp, color = NcColors.Text)
        }
    }
}

@Composable
private fun LeafLine(indent: Dp, label: String?, value: Any?) {
    val keyPart = if (label != null) "\"$label\": " else ""
    val (display, color) = when {
        value == null || value === JSONObject.NULL -> "null" to NcColors.Muted
        value is String -> "\"$value\"" to NcColors.StrGreen
        value is Number || value is Boolean -> value.toString() to NcColors.NumRed
        else -> value.toString() to NcColors.Text
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent + 18.dp, top = 1.dp, bottom = 1.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (keyPart.isNotEmpty()) {
            NcText(text = keyPart, size = 10.sp, color = NcColors.KeyBlue)
        }
        NcText(text = display, size = 10.sp, color = color)
    }
}
