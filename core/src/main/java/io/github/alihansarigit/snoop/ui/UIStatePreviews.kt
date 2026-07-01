package io.github.alihansarigit.snoop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.github.alihansarigit.snoop.model.NetworkTransaction
import io.github.alihansarigit.snoop.model.TransactionStatus

// ─────────────────────────────────────────────────────────────────────────────
// Sample UI states. Kept in one place so every @Preview draws from the same,
// deliberately diverse set of transactions (success, auth, errors, pending,
// failed, plain-text, empty…). Timestamps are fixed for stable rendering.
// ─────────────────────────────────────────────────────────────────────────────

private const val SAMPLE_EPOCH = 1_726_000_000_000L

private val BEARER_HEADER =
    "Authorization" to "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJkZW1vIn0.sig"

private fun sampleTx(
    id: Long,
    method: String,
    url: String,
    status: TransactionStatus = TransactionStatus.COMPLETE,
    responseCode: Int? = 200,
    requestHeaders: List<Pair<String, String>> = emptyList(),
    requestBody: String? = null,
    responseBody: String? = null,
    error: String? = null,
    durationMs: Long? = 128,
): NetworkTransaction {
    val afterScheme = url.substringAfter("://")
    return NetworkTransaction(
        id = id,
        method = method,
        url = url,
        host = afterScheme.substringBefore("/"),
        path = "/" + afterScheme.substringAfter("/", ""),
        requestHeaders = requestHeaders,
        requestBody = requestBody,
        requestContentType = requestBody?.let { "application/json" },
        responseCode = responseCode,
        responseBody = responseBody,
        responseContentType = responseBody?.let { "application/json" },
        status = status,
        error = error,
        requestDate = SAMPLE_EPOCH,
        durationMs = durationMs,
    )
}

/** A diverse set of states used to exercise every branch of the row/dialog UI. */
internal val previewTransactions: List<NetworkTransaction> = listOf(
    sampleTx(
        id = 1, method = "GET", url = "https://api.example.com/posts/1",
        responseBody = """{"id":1,"title":"hello","tags":["a","b"],"done":true}""",
    ),
    sampleTx(
        id = 2, method = "POST", url = "https://httpbin.org/post",
        requestHeaders = listOf("Content-Type" to "application/json", BEARER_HEADER),
        requestBody = """{"title":"snoop demo","userId":1}""",
        responseBody = """{"json":{"title":"snoop demo"},"headers":{"Authorization":"Bearer …"}}""",
        durationMs = 842,
    ),
    sampleTx(
        id = 3, method = "GET", url = "https://api.example.com/missing",
        responseCode = 404, responseBody = """{"error":"not found"}""", durationMs = 96,
    ),
    sampleTx(
        id = 4, method = "DELETE", url = "https://api.example.com/orders/42",
        responseCode = 500, responseBody = """{"error":"internal"}""", durationMs = 310,
    ),
    sampleTx(
        id = 5, method = "GET", url = "https://api.example.com/slow",
        status = TransactionStatus.PENDING, responseCode = null, durationMs = null,
    ),
    sampleTx(
        id = 6, method = "GET", url = "https://api.example.com/offline",
        status = TransactionStatus.FAILED, responseCode = null,
        error = "java.net.UnknownHostException", durationMs = 5,
    ),
    sampleTx(
        id = 7, method = "GET", url = "https://api.example.com/plain",
        responseBody = "just some plain text, not json", durationMs = 44,
    ),
    sampleTx(
        id = 8, method = "GET", url = "https://api.example.com/empty",
        responseCode = 204, responseBody = null, durationMs = 22,
    ),
)

/** Feeds one transaction per state into row-level previews. */
internal class UIStatePreviewProvider : PreviewParameterProvider<NetworkTransaction> {
    override val values: Sequence<NetworkTransaction> = previewTransactions.asSequence()
}

/** Feeds whole-list states (empty / few / mixed / all-errors) into dialog previews. */
internal class UIStateListPreviewProvider : PreviewParameterProvider<List<NetworkTransaction>> {
    override val values: Sequence<List<NetworkTransaction>> = sequenceOf(
        emptyList(),
        previewTransactions.take(3),
        previewTransactions,
        previewTransactions.filter { it.isError },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// One preview per UI surface.
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "LogRow · states", widthDp = 380, showBackground = true, backgroundColor = 0xFF111827)
@Composable
private fun LogRowPreview(@PreviewParameter(UIStatePreviewProvider::class) tx: NetworkTransaction) {
    Box(Modifier.background(NcColors.PanelBg).padding(8.dp)) {
        LogRow(tx = tx, copy = {})
    }
}

@Preview(name = "Dialog · list states", widthDp = 400, heightDp = 720, showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun DialogPreview(@PreviewParameter(UIStateListPreviewProvider::class) txs: List<NetworkTransaction>) {
    DebugLogsContent(transactions = txs, onDismiss = {})
}

@OptIn(ExperimentalLayoutApi::class)
@Preview(name = "Widgets · colors & opacity", widthDp = 380, showBackground = true, backgroundColor = 0xFF111827)
@Composable
private fun WidgetGalleryPreview() {
    val gap = Arrangement.spacedBy(6.dp)
    Column(
        modifier = Modifier.background(NcColors.PanelBg).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
            NcChip("ALL (12)", true, NcColors.FilterAll) {}
            NcChip("ERR (3)", false, NcColors.FilterErr) {}
            NcChip("…PEND (1)", false, NcColors.FilterPend) {}
        }
        FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
            NcButton("CLEAR", NcColors.ActionClear, idleDim = true) {}
            NcButton("ALL COPY LOG", NcColors.ActionCopyAll, idleDim = true) {}
            NcButton("GİZLE", NcColors.ActionHide, idleDim = true) {}
            NcButton("CLOSE", NcColors.ActionClose, idleDim = true) {}
        }
        FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
            NcToggle("[+] REQ", false, NcColors.ToggleReq) {}
            NcToggle("[-] REQ", true, NcColors.ToggleReq) {}
            NcToggle("[+] RES", false, NcColors.ToggleRes) {}
            NcToggle("[-] RES", true, NcColors.ToggleRes) {}
        }
        FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
            NcButton("ENDPOINT COPY", NcColors.CopyEndpoint, idleDim = true) {}
            NcButton("REQUEST COPY", NcColors.CopyRequest, idleDim = true) {}
            NcButton("RESPONSE COPY", NcColors.CopyResponse, idleDim = true) {}
            NcButton("CURL COPY", NcColors.CopyCurl, idleDim = true) {}
            NcButton("FULL COPY", NcColors.CopyFull, idleDim = true) {}
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NcBadge(3, NcColors.Ok)
            NcBadge(12, NcColors.Pending)
            NcBadge(128, NcColors.Err)
        }
        NcSearch(value = "", onValueChange = {}, placeholder = "filter by url…")
        NcSearch(value = "httpbin", onValueChange = {}, placeholder = "filter by url…")
    }
}

@Preview(name = "Bubble · badge states", showBackground = true, backgroundColor = 0xFF334155)
@Composable
private fun BubblePreview() {
    Row(
        modifier = Modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        BubbleGlyph(count = 0, badgeColor = NcColors.Ok)
        BubbleGlyph(count = 4, badgeColor = NcColors.Ok)
        BubbleGlyph(count = 23, badgeColor = NcColors.Pending)
        BubbleGlyph(count = 150, badgeColor = NcColors.Err)
    }
}

/** Static twin of the overlay bubble (no drag/global state) so it can be previewed. */
@Composable
private fun BubbleGlyph(count: Int, badgeColor: Color) {
    Box {
        Box(
            modifier = Modifier
                .size(48.dp)
                .shadow(8.dp, CircleShape)
                .background(NcColors.Bubble, CircleShape)
                .border(2.dp, Color.White, CircleShape),
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
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp),
            )
        }
    }
}
