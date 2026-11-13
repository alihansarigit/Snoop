package io.github.alihansarigit.snoop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.alihansarigit.snoop.network.NetworkMockRegistry

@Composable
internal fun NetworkToolsDialog(
    onInspector: () -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    var revision by remember { mutableIntStateOf(0) }
    val rules = remember(revision) { NetworkMockRegistry.snapshot() }

    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .padding(10.dp)
                .background(NcColors.PanelBg, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("NETWORK TOOLS", color = Color.White)
                    Text(
                        "${rules.size} active mock${if (rules.size == 1) "" else "s"}",
                        color = NcColors.Muted,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NcButton("BACK", bg = NcColors.Border, idleDim = true, onClick = onBack)
                    NcButton("CLOSE", bg = NcColors.ActionClose, idleDim = true, onClick = onClose)
                }
            }

            Button(onClick = onInspector, modifier = Modifier.fillMaxWidth()) {
                Text("OPEN INSPECTOR FOR REPLAY & MOCK")
            }

            if (rules.isNotEmpty()) {
                OutlinedButton(
                    onClick = {
                        NetworkMockRegistry.clear()
                        revision++
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("CLEAR ALL MOCK RULES", color = NcColors.ErrorText)
                }
            }

            if (rules.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NcColors.RowBg, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("No active mock rules", color = Color.White)
                    Text(
                        "Open Inspector, expand a request, then choose MOCK to create a rule.",
                        color = NcColors.Muted,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(rules, key = { it.id }) { rule ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NcColors.RowBg, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                "${rule.method} · ${rule.statusCode}",
                                color = NcColors.KeyBlue,
                            )
                            Text(rule.urlPattern, color = Color.White)
                            if (rule.body.isNotBlank()) {
                                Text(
                                    rule.body.take(180),
                                    color = NcColors.Muted,
                                    maxLines = 3,
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    NetworkMockRegistry.remove(rule.id)
                                    revision++
                                },
                            ) {
                                Text("REMOVE", color = NcColors.ErrorText)
                            }
                        }
                    }
                }
            }
        }
    }
}
