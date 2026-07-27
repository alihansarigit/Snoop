package io.github.alihansarigit.snoop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alihansarigit.snoop.model.NetworkTransaction
import io.github.alihansarigit.snoop.model.TransactionStatus
import io.github.alihansarigit.snoop.util.formatDuration
import io.github.alihansarigit.snoop.util.formatTime
import io.github.alihansarigit.snoop.util.fullDump
import io.github.alihansarigit.snoop.util.harExport
import io.github.alihansarigit.snoop.util.toCurl

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LogRow(tx: NetworkTransaction, copy: (String) -> Unit) {
    var reqOpen by remember(tx.id) { mutableStateOf(false) }
    var resOpen by remember(tx.id) { mutableStateOf(false) }

    val code = tx.responseCode
    val statusColor = when {
        tx.status == TransactionStatus.PENDING -> Color(0xFFFBBF24)
        tx.status == TransactionStatus.FAILED -> Color(0xFFF87171)
        code != null && code in 200..299 -> Color(0xFF34D399)
        code != null && code >= 400 -> Color(0xFFF87171)
        else -> Color(0xFFCBD5E1)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(NcColors.RowBg, RoundedCornerShape(6.dp))
            .pointerInput(tx.id) {
                detectTapGestures(onTap = {
                    val anyOpen = reqOpen || resOpen
                    reqOpen = !anyOpen
                    resOpen = !anyOpen
                })
            }
            .padding(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NcText(
                text = "${tx.method} ${code ?: "…"}",
                color = statusColor,
                size = 11.sp,
                weight = FontWeight.Bold,
            )
            NcText("   ")
            NcText(
                text = "${formatTime(tx.requestDate)}  ·  ${formatDuration(tx.durationMs)}",
                size = 10.sp,
                color = NcColors.Muted,
            )
        }
        NcText(tx.url, size = 10.sp)

        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            NcToggle(if (reqOpen) "[-] REQ" else "[+] REQ", reqOpen, NcColors.ToggleReq) { reqOpen = !reqOpen }
            NcToggle(if (resOpen) "[-] RES" else "[+] RES", resOpen, NcColors.ToggleRes) { resOpen = !resOpen }
            NcButton("ENDPOINT COPY", bg = NcColors.CopyEndpoint, idleDim = true) { copy(tx.url) }
            NcButton("REQUEST COPY", bg = NcColors.CopyRequest, idleDim = true) { copy(tx.requestBody.orEmpty()) }
            NcButton("RESPONSE COPY", bg = NcColors.CopyResponse, idleDim = true) { copy(tx.responseBody.orEmpty()) }
            NcButton("CURL COPY", bg = NcColors.CopyCurl, idleDim = true) { copy(tx.toCurl()) }
            NcButton("FULL COPY", bg = NcColors.CopyFull, idleDim = true) { copy(tx.fullDump()) }
            NcButton("HAR COPY", bg = NcColors.CopyHar, idleDim = true) { copy(harExport(listOf(tx))) }
        }

        if (reqOpen) {
            HeaderLines("REQUEST HEADERS", tx.requestHeaders)
            JsonBody(tx.requestBody, NcColors.KeyBlue, "(no request body)")
        }
        if (resOpen) {
            HeaderLines("RESPONSE HEADERS", tx.responseHeaders)
            JsonBody(tx.responseBody, NcColors.StrGreen, "(no response body)")
        }
    }
}

/** Renders a captured header block as `Name: value` lines; the name is tinted, long
 *  values (tokens, cookies) wrap instead of overflowing. Nothing shows when empty. */
@Composable
private fun HeaderLines(label: String, headers: List<Pair<String, String>>) {
    if (headers.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        NcText(label, size = 9.sp, color = NcColors.Muted, weight = FontWeight.Bold)
        headers.forEach { (name, value) ->
            NcText(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = NcColors.KeyBlue, fontWeight = FontWeight.Bold)) {
                        append(name)
                    }
                    append(": ")
                    append(value)
                },
                size = 10.sp,
            )
        }
    }
}
