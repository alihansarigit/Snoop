# Snoop ADB/MCP bridge

The `daemon` module supports two local modes:

- the default newline-delimited MCP stdio server, which opens no network port;
- an optional project bridge that listens only on host loopback and is reached
  from an authorized Android device through `adb reverse`.

Neither mode sends feedback to an external service.

## Build

```bash
./gradlew :daemon:installDist
```

Executable:

```text
<repository>/daemon/build/install/snoop-mcp/bin/snoop-mcp
```

The Android application must be a debuggable build because the bridge uses
`adb exec-out run-as <package>`.

## MCP configuration

```json
{
  "mcpServers": {
    "snoop": {
      "command": "/absolute/path/to/Snoop/daemon/build/install/snoop-mcp/bin/snoop-mcp",
      "env": {
        "SNOOP_PACKAGE": "com.example.app.debug"
      }
    }
  }
}
```

Do not run the server through `gradlew run`: Gradle writes progress to stdout,
which would corrupt the stdio protocol.

## Tools

- `snoop_list_sessions`: reads and returns session manifests.
- `snoop_get_session`: returns `feedback.md`, falling back to the live JSON
  manifest when the session is still open.
- `snoop_pull_session`: writes a tar archive containing the manifest, Markdown
  and attachments into a caller-selected local directory.

`package_name` can be supplied per tool call instead of setting
`SNOOP_PACKAGE`. Package names and session ids are strictly validated before
they reach ADB. The bridge never constructs a shell command from an output
directory; that path is used only by the local JVM process.

## One-tap project export

Consumers do not run this by hand — the `io.github.alihansarigit.snoop` Gradle
plugin starts the bridge as part of `installDebug`. See the README. The command
below is what the plugin executes, and is useful when debugging the daemon
itself:

```bash
<repository>/daemon/build/install/snoop-mcp/bin/snoop-mcp bridge \
  --project-dir "/absolute/path/to/app-project" \
  --package "com.example.app.debug" \
  --idle-timeout 480
```

Options can also be supplied through `SNOOP_PROJECT_DIR`, `SNOOP_PACKAGE`,
`SNOOP_BRIDGE_PORT` (default `8737`) and `SNOOP_BRIDGE_IDLE_TIMEOUT` (minutes,
default `480`, `0` disables). The idle watchdog exists because an
automatically started bridge must not accumulate one process per project per
day. While the process is running it refreshes `adb reverse` for every
authorized device or emulator. The Sessions UI shows:

- red with recovery instructions when the daemon/ADB route is unavailable;
- amber while checking or transferring;
- green with the resolved desktop project when the complete route is healthy.

Opening a session and choosing **Send to project** writes:

```text
<project>/agent-feedback/<transfer-day>/<application-id>/<session>/full/
├── <session-id>-agent-feedback.zip
├── feedback.md
├── manifest.json
└── attachments/
```

The day comes from the daemon's own clock at transfer time, and `<session>` is
the session id's random suffix — the day already names an ancestor, and the full
id remains in `manifest.json`.

Ticking individual pins and choosing **Export to IDE** sends only those, beside
the session's `full` directory and named after the pins it carries:

```text
<project>/agent-feedback/<transfer-day>/<application-id>/<session>/pin2-5-7/
```

The request carries a `selection` field for this; absent means `full`. The
partial bundle carries a manifest rebuilt from the selection rather than a copy
of the session manifest, so the unselected pins are absent from the export
rather than merely unreferenced. Giving it its own directory means a partial
export never replaces the full-session export.

The Android app cannot select an arbitrary desktop path. The daemon constrains
all writes to the project directory chosen at startup, validates package and
session identifiers, limits bundle size, and rejects unsafe ZIP entries.
Re-sending a session replaces its directory, so attachments deleted in the app
since the previous transfer do not linger.

The `status` handshake reports `protocolVersion`. The app refuses to transfer
when it does not match, rather than failing somewhere inside the export.

## USB and Wi-Fi

Any device shown as `device` by `adb devices` can be used. For wireless use,
pair/connect the device through Android's Wireless debugging screen first; the
bridge does not weaken or automate ADB authorization.
