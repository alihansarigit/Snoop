package io.github.alihansarigit.snoop.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.alihansarigit.snoop.Snoop
import io.github.alihansarigit.snoop.ktor.SnoopKtor
import io.github.alihansarigit.snoop.okhttp.SnoopInterceptor
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class MainActivity : ComponentActivity() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val client by lazy {
        OkHttpClient.Builder()
            .addInterceptor(SnoopInterceptor())
            .build()
    }

    // A Ktor client captured by the same overlay — install the plugin and go.
    private val ktorClient by lazy {
        HttpClient(OkHttp) {
            install(SnoopKtor)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Snoop.registerDeveloperAction("Clear response mocks", Runnable(Snoop::clearNetworkMocks))
        Snoop.registerDeveloperAction("Restart sample", Runnable { Snoop.restartApp(this) })
        Snoop.setFeedbackScreenContext(
            routeName = "sample",
            subScreen = "network-actions",
            screenFileHint = "MainActivity.kt",
        )
        setContent {
            MaterialTheme {
                var shakeEnabled by remember { mutableStateOf(Snoop.shakeToShow) }
                SampleScreen(
                    onGet = { fetch("https://jsonplaceholder.typicode.com/posts/1") },
                    onList = { fetch("https://jsonplaceholder.typicode.com/posts") },
                    onPost = ::post,
                    onBearer = ::fetchWithBearer,
                    onKtorGet = { ktorGet("https://jsonplaceholder.typicode.com/posts/1") },
                    onKtorPost = ::ktorPost,
                    onError = { fetch("https://jsonplaceholder.typicode.com/does-not-exist") },
                    onOpenInspector = { Snoop.launchInspector(this) },
                    onCaptureFeedback = { Snoop.launchCapture(this) },
                    onStartSession = {
                        Snoop.startSession("Sample checkout ${System.currentTimeMillis()}")
                    },
                    onMockNextGet = {
                        Snoop.addNetworkMock(
                            method = "GET",
                            urlPattern = "https://jsonplaceholder.typicode.com/posts/1",
                            statusCode = 503,
                            body = """{"error":"Snoop sample mock"}""",
                        )
                    },
                    onCrash = {
                        Snoop.recordAction("sample_crash_button", "tap")
                        error("Intentional Snoop sample crash")
                    },
                    shakeEnabled = shakeEnabled,
                    onToggleShake = {
                        Snoop.shakeToShow = !Snoop.shakeToShow
                        shakeEnabled = Snoop.shakeToShow
                    },
                )
            }
        }
    }

    private fun fetch(url: String) {
        scope.launch {
            runCatching {
                client.newCall(Request.Builder().url(url).build()).execute().use { it.body?.string() }
            }
        }
    }

    private fun post() {
        scope.launch {
            runCatching {
                val payload = """
                    {
                      "title": "snoop demo",
                      "userId": 1,
                      "tags": ["compose", "okhttp", "debug"],
                      "meta": { "priority": "high", "draft": false, "views": 1234 }
                    }
                """.trimIndent().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("https://httpbin.org/post")
                    // httpbin echoes headers + body back, so the Bearer token shows
                    // up in the captured response too — not just in CURL/FULL COPY.
                    .header("Authorization", "Bearer $bearerToken")
                    .post(payload)
                    .build()
                client.newCall(request).execute().use { it.body?.string() }
            }
        }
    }

    // Demo JWT — httpbin.org/bearer just echoes it back. The point is that Snoop
    // captures the outgoing `Authorization: Bearer …` header in the request view.
    private val bearerToken =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
            "eyJzdWIiOiJuZXRjZW50LWRlbW8iLCJuYW1lIjoiSmFuZSBEb2UiLCJpYXQiOjE3MDAwMDAwMDB9." +
            "s3cr3t_demo_signature_not_verified"

    private fun fetchWithBearer() {
        scope.launch {
            runCatching {
                val request = Request.Builder()
                    .url("https://httpbin.org/bearer")
                    .header("Authorization", "Bearer $bearerToken")
                    .build()
                client.newCall(request).execute().use { it.body?.string() }
            }
        }
    }

    private fun ktorGet(url: String) {
        scope.launch {
            runCatching { ktorClient.get(url).bodyAsText() }
        }
    }

    private fun ktorPost() {
        scope.launch {
            runCatching {
                ktorClient.post("https://httpbin.org/post") {
                    header("Authorization", "Bearer $bearerToken")
                    contentType(ContentType.Application.Json)
                    setBody("""{"title":"snoop ktor demo","userId":1,"via":"ktor"}""")
                }.bodyAsText()
            }
        }
    }
}

@Composable
private fun SampleScreen(
    onGet: () -> Unit,
    onList: () -> Unit,
    onPost: () -> Unit,
    onBearer: () -> Unit,
    onKtorGet: () -> Unit,
    onKtorPost: () -> Unit,
    onError: () -> Unit,
    onOpenInspector: () -> Unit,
    onCaptureFeedback: () -> Unit,
    onStartSession: () -> Unit,
    onMockNextGet: () -> Unit,
    onCrash: () -> Unit,
    shakeEnabled: Boolean,
    onToggleShake: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Snoop Sample", style = MaterialTheme.typography.headlineSmall)
        Text("Fire requests, then tap the floating bubble to open the Debug Hub.")
        Button(onClick = onGet, modifier = Modifier.fillMaxWidth()) { Text("GET /posts/1") }
        Button(onClick = onList, modifier = Modifier.fillMaxWidth()) { Text("GET /posts (list)") }
        Button(onClick = onPost, modifier = Modifier.fillMaxWidth()) { Text("POST /post (auth)") }
        Button(onClick = onBearer, modifier = Modifier.fillMaxWidth()) { Text("GET /bearer (auth)") }
        Button(onClick = onKtorGet, modifier = Modifier.fillMaxWidth()) { Text("Ktor GET /posts/1") }
        Button(onClick = onKtorPost, modifier = Modifier.fillMaxWidth()) { Text("Ktor POST /post (auth)") }
        Button(onClick = onError, modifier = Modifier.fillMaxWidth()) { Text("GET 404") }
        Button(onClick = onOpenInspector, modifier = Modifier.fillMaxWidth()) { Text("Open inspector") }
        Button(onClick = onCaptureFeedback, modifier = Modifier.fillMaxWidth()) { Text("Capture feedback") }
        Button(onClick = onStartSession, modifier = Modifier.fillMaxWidth()) { Text("Start feedback session") }
        Button(onClick = onMockNextGet, modifier = Modifier.fillMaxWidth()) { Text("Mock next GET as 503") }
        Button(onClick = onCrash, modifier = Modifier.fillMaxWidth()) { Text("Trigger recovery crash") }
        Button(onClick = onToggleShake, modifier = Modifier.fillMaxWidth()) {
            Text("Shake-to-show: ${if (shakeEnabled) "ON" else "OFF"}")
        }
}
}
