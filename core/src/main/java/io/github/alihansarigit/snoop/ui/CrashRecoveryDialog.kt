package io.github.alihansarigit.snoop.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.alihansarigit.snoop.Snoop
import io.github.alihansarigit.snoop.feedback.PendingCrash

@Composable
internal fun CrashRecoveryDialog(crash: PendingCrash, screenshot: Bitmap?) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Dialog(onDismissRequest = Snoop::dismissPendingCrash) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .background(NcColors.PanelBg, androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Snoop recovered a crash", color = Color.White)
            Text(
                "Thread: ${crash.threadName}. The application was allowed to terminate safely; " +
                    "this report was restored on the next launch.",
                color = NcColors.Muted,
            )
            if (screenshot != null) {
                Image(
                    bitmap = screenshot.asImageBitmap(),
                    contentDescription = "Last screen before crash",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                crash.stackTrace.lineSequence().take(12).joinToString("\n"),
                color = Color(0xFFFCA5A5),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    Snoop.pendingCrashMarkdown()?.let {
                        clipboard.setText(AnnotatedString(it))
                    }
                }) { Text("COPY REPORT") }
                Button(onClick = Snoop::attachPendingCrash) { Text("ATTACH") }
            }
            Button(
                onClick = {
                    Snoop.dismissPendingCrash()
                    Snoop.restartApp(context)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("RESTART APP CLEANLY") }
            NcButton("DISMISS", bg = NcColors.ActionClose, idleDim = true) {
                Snoop.dismissPendingCrash()
            }
        }
    }
}
