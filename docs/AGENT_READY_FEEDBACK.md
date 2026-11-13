# Agent-ready feedback architecture

Snoop remains Android-first and debug-only. The public release swap continues
to use `snoop-no-op`, which mirrors the real entry-point API without installing
providers, permissions, crash handlers, sensors, storage or network hooks.

## Runtime flow

1. `androidx.startup` installs Snoop and restores session/crash metadata.
2. The activity overlay observes lifecycle transitions, foreground touches and
   shake events.
3. A shake or `Snoop.launchCapture()` snapshots both the visible window and its
   selectable UI targets, then opens a transparent marking layer over the live
   app UI. The clean screenshot remains in memory for cropping and export.
4. A tap creates a numbered component pin. A drag adds a cropped region; several
   regions can be attached to the same pin. Pins have editable component names
   and voice/text notes. Pen, circle and arrow annotation remain available, with
   one undo history for pins and drawings.
5. Review opens as a bottom sheet for pin editing, category selection, overall
   context, Markdown copy and the final save action.
6. Saving creates or reuses an active session, captures a privacy-safe
   accessibility tree, recent breadcrumbs/logs and the newest network ids, then
   atomically updates the session manifest.
7. Closing the session writes `feedback.md` beside its attachment directory.

## Storage layout

```text
files/snoop/feedback/
├── active-session
├── custom-categories.json
├── pending-crash.json
└── sessions/
    └── <session-id>/
        ├── manifest.json
        ├── feedback.md
        └── attachments/
            ├── <item-id>-screenshot.png
            └── <item-id>-pin<pin-id>-<region-index>.png
```

The session manifest has an explicit schema version. Sensitive HTTP headers and
configured JSON keys are redacted in `NetworkLogStore` before UI, persistence,
HAR, feedback export or MCP access can see them.

## Crash semantics

The uncaught-exception handler performs only bounded synchronous work:

- serialize the throwable;
- copy the already-compressed last-known-good screenshot;
- snapshot the last breadcrumbs and 30-second/100-line log ring;
- collect non-sensitive package/device fields;
- atomically write `pending-crash.json`;
- delegate to the handler that was installed before Snoop.

Snoop does not attempt to render Compose UI on a failed UI thread and does not
swallow fatal exceptions. The recovery dialog appears on the next process
launch and can copy or attach the report.

## UI and semantics privacy

Accessibility snapshots omit visible text and editable values. They retain
class names, resource ids, content descriptions, bounds and interaction flags.
The capture target resolver keeps short potential-target labels in memory so it
can propose a component name for the point the developer explicitly pins;
password nodes are never read and only the chosen target is persisted. The
suggested name is editable before saving.
Host applications should call `Snoop.recordAction()` for stable domain actions
and test tags that are not exposed through accessibility.

## Network mocking and replay

Mock patterns are bounded, support only `*` wildcards and are matched without
executing user-provided regular expressions. OkHttp returns a synthetic
`Response`; Ktor returns a client-owned synthetic `HttpClientCall`. Replay
handlers are adapter-owned and bounded to the newest 200 transactions.

## Platform boundary

The persistence, capture, crash, speech, activity and ADB implementations are
Android/JVM-specific. Domain objects and export rules are isolated so a future
Compose Multiplatform implementation can replace the platform services without
changing the report format.
