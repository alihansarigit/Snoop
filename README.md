# Snoop

Drop-in network logging debug overlay for **Android Compose** apps. A draggable
bubble floats over your app; tap it to inspect every HTTP request and response —
status, timing, headers, and pretty-printed JSON bodies. Inert in release builds.

> Status: `0.2.0` — early but working. OkHttp & Ktor capture.

## Demo

<video src="https://github.com/alihansarigit/Snoop/raw/main/sample/video.gif" controls muted width="300"></video>

Drag the bubble, tap it, and inspect every OkHttp / Ktor request — status, timing,
headers, and pretty-printed JSON. If the player doesn't load, [watch the clip](sample/video.mp4).

## Features

- 🐞 **Draggable debug bubble** with a request-count badge (green / amber / red), anchored top-right — auto-attached to every activity, no code in your UI tree
- 🪟 **Dialog inspector** (not a separate screen) with search and ALL / ERR / PEND filters
- 🌳 **Collapsible JSON tree** — expand/collapse request & response bodies with `[+]`/`[-]`
- 📋 **Copy buttons** per request: FULL, REQUEST, RESPONSE, ENDPOINT, **cURL**, plus a global **ALL COPY LOG**
- 🙈 **Hide** the overlay from inside the dialog (GİZLE); restore with `Snoop.show()`
- 📳 **Shake to reveal** — shake the device to bring a hidden bubble back; toggle off with `Snoop.shakeToShow = false`
- 🔌 **OkHttp & Ktor** capture — a single interceptor or one client plugin (transport-agnostic core)
- 🧩 **Custom sections** — inject your own debug controls (env switch, feature flags…) into the dialog
- 🚫 **No-op in release** — a separate artifact strips everything out

## Modules

| Artifact | Purpose |
|----------|---------|
| `snoop-core` | Compose UI overlay + in-memory log store |
| `snoop-okhttp` | OkHttp `Interceptor` that feeds the store |
| `snoop-ktor` | Ktor client plugin that feeds the store |
| `snoop-no-op` | Empty drop-in replacement for release builds |

## Install

```kotlin
dependencies {
    // Pick the adapter matching your HTTP client (both pull in snoop-core):
    debugImplementation("io.github.alihansarigit:snoop-okhttp:0.2.0")   // OkHttp
    // debugImplementation("io.github.alihansarigit:snoop-ktor:0.2.0")  // Ktor
    releaseImplementation("io.github.alihansarigit:snoop-no-op:0.2.0")
}
```

`snoop-okhttp` and `snoop-ktor` each pull in `snoop-core` transitively — add both
if your app mixes clients. `snoop-no-op` mirrors every adapter's public API, so the
release swap compiles unchanged. While unpublished, use a local build: run
`./gradlew publishToMavenLocal` and add `mavenLocal()` to your repositories.

## 🤖 Let an AI integrate it

Open your project in an AI coding assistant (Claude Code, Cursor, Copilot Chat,
ChatGPT…) and paste the prompt below. On GitHub, hover the block and click the
**copy icon** in its top-right corner.

```text
Add the Snoop network-inspector library to my existing Android app. Snoop is a
drop-in debug overlay: a draggable bubble floats over debug builds and lets me
inspect every OkHttp request/response (status, timing, headers, pretty-printed
JSON). It is inert in release builds via a separate no-op artifact.

Integrate it, adapting to my build setup and code style:

1. Gradle dependencies — add to the app module and KEEP the debug/release split
   exactly (real library in debug only, no-op in release only):
       debugImplementation("io.github.alihansarigit:snoop-okhttp:0.2.0")
       releaseImplementation("io.github.alihansarigit:snoop-no-op:0.2.0")
   Make sure mavenCentral() is in the repositories. snoop-okhttp pulls in
   snoop-core transitively. If I use a version catalog (libs.versions.toml),
   put the coordinates there and reference them.

2. Interceptor — find where I build OkHttpClient and add it LAST, so it sees the
   final request:
       import io.github.alihansarigit.snoop.okhttp.SnoopInterceptor
       OkHttpClient.Builder()
           .addInterceptor(SnoopInterceptor())
           .build()
   The constructor arg is optional: SnoopInterceptor(maxBodyBytes = 250_000L).
   Add it to every client whose traffic I want to see.

3. Nothing else is required — the bubble auto-installs via androidx.startup.
   The io.github.alihansarigit.snoop.Snoop object is optional:
       Snoop.registerSection("Env") { /* @Composable debug controls */ }
       Snoop.launchInspector(context)   // open the inspector directly
       Snoop.hide() / Snoop.show() / Snoop.clear()
       Snoop.shakeToShow = false         // disable shake-to-reveal (default on)

Rules:
- The app is Jetpack Compose; activities are ComponentActivity/AppCompatActivity.
- This recipe wires the OkHttp adapter; if I use Ktor, install the `snoop-ktor`
  plugin instead: `HttpClient(engine) { install(SnoopKtor) }`.
- Reference the Snoop API from debug-only code paths — the no-op artifact ships
  the same class names with empty bodies, so debug-only usage keeps release clean.
- Show me the exact diffs, then the Gradle sync/build command to run.
```

## Usage

Add the interceptor to your OkHttp client. That's it — the bubble auto-installs
through `androidx.startup`.

```kotlin
val client = OkHttpClient.Builder()
    .addInterceptor(SnoopInterceptor())
    .build()
```

### Ktor

Using Ktor instead? Install the plugin on your `HttpClient` — same overlay, same
store. Works with any engine (CIO, OkHttp, Android…).

```kotlin
import io.github.alihansarigit.snoop.ktor.SnoopKtor

val client = HttpClient(engine) {
    install(SnoopKtor)
    // optional: cap captured bodies (default 250 KB)
    // install(SnoopKtor) { maxBodyBytes = 500_000 }
}
```

### Custom sections (optional)

```kotlin
Snoop.registerSection("Environment") {
    // any @Composable — appears at the top of the inspector
    EnvironmentSwitch()
}
```

### Manual control (optional)

```kotlin
Snoop.hide()                 // hide the bubble (same as the dialog's GİZLE)
Snoop.show()                 // bring it back
Snoop.clear()                // clear captured logs
Snoop.launchInspector(ctx)   // open the logs dialog directly
Snoop.shakeToShow = false    // opt out of shake-to-reveal (default: on)
```

### Shake to reveal

After you hide the bubble with **GİZLE**, give the device a firm shake to bring it
back — handy when you don't want to keep the overlay on screen but still need it a
tap away. It's on by default; disable it with `Snoop.shakeToShow = false` (e.g. if
your app already uses shake for something else). Uses the accelerometer only while an
activity is in the foreground, and needs no permission.

## How release stripping works

`snoop-no-op` ships the **same public classes** (`Snoop`,
`SnoopInterceptor`) with empty bodies. Because you depend on the real modules
only via `debugImplementation` and the no-op via `releaseImplementation`, release
builds compile unchanged and capture nothing — zero overhead, no logs in
production.

## Limitations (0.1.0)

- Host activities must be `ComponentActivity`/`AppCompatActivity` (standard for
  Compose apps) — the bubble relies on the view-tree lifecycle owner.
- Capture adapters: OkHttp and Ktor. In-memory (text/JSON) bodies are captured;
  streaming request bodies and Server-Sent Event responses are recorded
  metadata-only, so the stream is never disturbed.
- Logs are in-memory (ring buffer, newest 200) and not persisted.

## Building

```bash
./gradlew :sample:assembleDebug      # demo app with the live overlay
./gradlew runOnEmulator              # boot an emulator, install & launch the sample
./gradlew publishToMavenLocal        # publish libraries to ~/.m2
```

`runOnEmulator` boots the `Pixel_8_Pro` AVD (reusing any already-connected device),
installs the debug sample, and launches it so the overlay is live. Pick a different
AVD with `-Psnoop.avd=<name>`, or just boot one with `./gradlew bootEmulator`.

See [docs/PUBLISHING.md](docs/PUBLISHING.md) for Maven Central release steps.

## License

Apache-2.0 — see [LICENSE](LICENSE).
