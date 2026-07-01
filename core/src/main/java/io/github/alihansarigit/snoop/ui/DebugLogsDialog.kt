package io.github.alihansarigit.snoop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.alihansarigit.snoop.Snoop
import io.github.alihansarigit.snoop.model.NetworkTransaction
import io.github.alihansarigit.snoop.model.TransactionStatus
import io.github.alihansarigit.snoop.util.allLogsDump

private enum class LogFilter { ALL, ERRORS, PENDING }

@Composable
internal fun DebugLogsDialog(onDismiss: () -> Unit) {
    val transactions by Snoop.store.transactions.collectAsStateWithLifecycle()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        DebugLogsContent(transactions = transactions, onDismiss = onDismiss)
    }
}

/** Stateless dialog body — split out from [DebugLogsDialog] so previews can drive it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DebugLogsContent(
    transactions: List<NetworkTransaction>,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val copy: (String) -> Unit = { clipboard.setText(AnnotatedString(it)) }

    var filter by remember { mutableStateOf(LogFilter.ALL) }
    var query by remember { mutableStateOf("") }

    val filtered = remember(transactions, filter, query) {
        transactions.filter { e ->
            val byFilter = when (filter) {
                LogFilter.ALL -> true
                LogFilter.ERRORS -> e.isError
                LogFilter.PENDING -> e.status == TransactionStatus.PENDING
            }
            val byQuery = query.isBlank() || e.url.contains(query, ignoreCase = true)
            byFilter && byQuery
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
            .padding(horizontal = 8.dp, vertical = 16.dp)
            .background(NcColors.PanelBg, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            NcText(
                text = "NETWORK LOGS  ${filtered.size}/${transactions.size}",
                size = 13.sp,
                weight = FontWeight.Bold,
            )
            NcButton("CLOSE", bg = NcColors.ActionClose, idleDim = true) { onDismiss() }
        }

        Spacer(Modifier.height(8.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            NcChip("ALL (${transactions.size})", filter == LogFilter.ALL, NcColors.FilterAll) { filter = LogFilter.ALL }
            val errorCount = transactions.count { it.isError }
            NcChip("ERR ($errorCount)", filter == LogFilter.ERRORS, NcColors.FilterErr) { filter = LogFilter.ERRORS }
            val pendingCount = transactions.count { it.status == TransactionStatus.PENDING }
            NcChip("…PEND ($pendingCount)", filter == LogFilter.PENDING, NcColors.FilterPend) { filter = LogFilter.PENDING }
            NcButton("CLEAR", bg = NcColors.ActionClear, idleDim = true) { Snoop.clear() }
            NcButton("ALL COPY LOG", bg = NcColors.ActionCopyAll, idleDim = true) { copy(allLogsDump(filtered)) }
            NcButton("GİZLE", bg = NcColors.ActionHide, idleDim = true) {
                onDismiss()
                Snoop.hide()
            }
        }

        Spacer(Modifier.height(8.dp))

        if (Snoop.customSections.isNotEmpty()) {
            Snoop.customSections.forEach { section ->
                NcText(section.title, size = 11.sp, color = NcColors.Muted, weight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                section.content()
                Spacer(Modifier.height(8.dp))
            }
        }

        NcSearch(value = query, onValueChange = { query = it }, placeholder = "filter by url…")

        Spacer(Modifier.height(6.dp))
        NcText("─".repeat(48), size = 10.sp, color = NcColors.Border)

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                NcText(
                    text = if (transactions.isEmpty()) "No requests yet" else "No match",
                    size = 12.sp,
                    color = NcColors.Muted,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filtered.reversed(), key = { it.id }) { tx ->
                    LogRow(tx, copy)
                }
            }
        }
    }
}
