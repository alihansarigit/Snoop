package io.github.alihansarigit.snoop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.alihansarigit.snoop.Snoop
import io.github.alihansarigit.snoop.model.NetworkTransaction

@Composable
internal fun NetworkMockDialog(
    transaction: NetworkTransaction,
    onDismiss: () -> Unit,
) {
    var pattern by remember { mutableStateOf(transaction.url) }
    var status by remember { mutableStateOf((transaction.responseCode ?: 200).toString()) }
    var body by remember { mutableStateOf(transaction.responseBody.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NcColors.PanelBg, RoundedCornerShape(14.dp))
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("CREATE RESPONSE MOCK", color = Color.White)
            Text(
                "Future ${transaction.method} requests matching this URL will be answered locally.",
                color = NcColors.Muted,
            )
            OutlinedTextField(
                value = pattern,
                onValueChange = { pattern = it.take(1_000) },
                label = { Text("URL pattern (* supported)") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = status,
                onValueChange = { status = it.filter(Char::isDigit).take(3) },
                label = { Text("HTTP status") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it.take(1_000_000) },
                label = { Text("Response body") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 6,
            )
            error?.let { Text(it, color = Color(0xFFFCA5A5)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val code = status.toIntOrNull()
                    if (code == null || code !in 100..599) {
                        error = "Status must be between 100 and 599."
                    } else {
                        runCatching {
                            Snoop.addNetworkMock(
                                method = transaction.method,
                                urlPattern = pattern,
                                statusCode = code,
                                body = body,
                                headers = mapOf("Content-Type" to (transaction.responseContentType ?: "application/json")),
                            )
                        }.onSuccess { onDismiss() }
                            .onFailure { error = it.message ?: "Unable to create mock." }
                    }
                }) { Text("ENABLE MOCK") }
                NcButton("CANCEL", bg = NcColors.ActionClose, idleDim = true, onClick = onDismiss)
            }
        }
    }
}
