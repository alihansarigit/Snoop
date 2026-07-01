# Snoop

Drop-in network logging debug overlay for **Android Compose** apps. A draggable
bubble floats over your app; tap it to inspect every HTTP request and response —
status, timing, headers, and pretty-printed JSON bodies. Inert in release builds.

> Status: `0.1.0-SNAPSHOT` — early, working, not yet on Maven Central.

## Features

- 🐞 **Draggable debug bubble** with a request-count badge (green / amber / red), anchored top-right — auto-attached to every activity, no code in your UI tree
- 🪟 **Dialog inspector** (not a separate screen) with search and ALL / ERR / PEND filters
- 🌳 **Collapsible JSON tree** — expand/collapse request & response bodies with `[+]`/`[-]`
- 📋 **Copy buttons** per request: FULL, REQUEST, RESPONSE, ENDPOINT, **cURL**, plus a global **ALL COPY LOG**
- 🙈 **Hide** the overlay from inside the dialog (GİZLE); restore with `Snoop.show()`
- 📳 **Shake to reveal** — shake the device to bring a hidden bubble back; toggle off with `Snoop.shakeToShow = false`
- 🔌 **OkHttp** capture via a single interceptor (transport-agnostic core)
- 🧩 **Custom sections** — inject your own debug controls (env switch, feature flags…) into the dialog
- 🚫 **No-op in release** — a separate artifact strips everything out

## Modules

| Artifact | Purpose |
|----------|---------|
| `snoop-core` | Compose UI overlay + in-memory log store |
| `snoop-okhttp` | OkHttp `Interceptor` that feeds the store |
| `snoop-no-op` | Empty drop-in replacement for release builds |

## Install

```kotlin
dependencies {
    debugImplementation("io.github.alihansarigit:snoop-okhttp:0.1.0")
    releaseImplementation("io.github.alihansarigit:snoop-no-op:0.1.0")
}
```

`snoop-okhttp` pulls in `snoop-core` transitively. While unpublished, use a
local build: run `./gradlew publishToMavenLocal` and add `mavenLocal()` to your
repositories.

## Usage

Add the interceptor to your OkHttp client. That's it — the bubble auto-installs
through `androidx.startup`.

```kotlin
val client = OkHttpClient.Builder()
    .addInterceptor(SnoopInterceptor())
    .build()
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
- Capture adapter is OkHttp only. A Ktor plugin is planned.
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
