package io.github.alihansarigit.snoop.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

internal fun formatTime(epochMillis: Long): String =
    if (epochMillis <= 0) "" else timeFormat.format(Date(epochMillis))

internal fun formatDuration(ms: Long?): String = when {
    ms == null -> "…"
    ms < 1000 -> "$ms ms"
    else -> String.format(Locale.US, "%.2f s", ms / 1000.0)
}

internal fun formatSize(bytes: Long): String = when {
    bytes < 0 -> ""
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}
