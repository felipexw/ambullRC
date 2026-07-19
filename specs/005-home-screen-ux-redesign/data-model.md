# Phase 1 Data Model: Home Screen UX Redesign

## Entity: Connection Status Presentation (derived, not stored)

A pure mapping from the existing `ConnectionState` (unchanged, see
`specs/002-esp32-bluetooth-connection/`) to the visual attributes FR-001/FR-002/FR-003/FR-004
require. Not a new stored type — implemented the same way `ConnectionStatusBar.statusText()`
already maps `ConnectionState` to a string today, just returning more attributes.

| Attribute | Type | Notes |
|---|---|---|
| label | String | "Connecting…" / "Connected" / "Disconnected" |
| backgroundColor | Color | one of the three status-container tokens (amber/green/red container) |
| foregroundColor | Color | matching on-container token, used for label + Retry button text |
| dotColor | Color | matching accent-strength token (amber/green/red dot) |
| animated | Boolean | `true` only while `Connecting` (drives the pulse) |
| showRetry | Boolean | `true` only for the "disconnected" bucket |

### `ConnectionState` → presentation mapping (resolves spec Assumption 2)

| `ConnectionState` | Bucket | label | animated | showRetry |
|---|---|---|---|---|
| `Idle` | disconnected | "Disconnected" | false | true |
| `Connecting` | connecting | "Connecting…" | true | false |
| `Connected` | connected | "Connected" | false | false |
| `Failed(reason)` | disconnected | "Disconnected" | false | true |

`Failed`'s existing `reason` (`PERMISSION_DENIED` / `BLUETOOTH_DISABLED` / `DEVICE_UNAVAILABLE` /
`CONNECTION_LOST` / `ERROR`) is still recorded in the log panel (see below) — the header pill
itself only ever shows the three driver-facing buckets, per spec FR-001.

## Entity: Log Entry

Replaces feature 004's plain `String` entry now that FR-012/SC-004 require per-entry color-coding.

| Attribute | Type | Notes |
|---|---|---|
| timestamp | `java.time.LocalTime` | Captured at `add()` time; rendered as `HH:mm:ss.SSS` (matches design). |
| category | `LogCategory` | See below — drives the tag's color and 2-3 letter label. |
| level | `LogLevel` | `INFO` \| `WARN` \| `ERROR` — drives the message text's color. |
| message | String | Human-readable description, same content as feature 004's messages. |

```kotlin
enum class LogCategory(val tag: String) {
    SENT("TX"),        // ControlViewModel: a direction command was written to the link
    RECEIVED("RX"),     // reserved — see research.md Decision 5; no current call site
    CONNECTION("BLE"),  // ConnectionViewModel: connect/retry/drop lifecycle events
    APP("APP"),         // reserved for general app-level events (e.g. permission denied)
}

enum class LogLevel { INFO, WARN, ERROR }
```

### Message content by call site (extends feature 004's table with category/level)

| Source | Trigger | category | level | message |
|---|---|---|---|---|
| `ConnectionViewModel` | `connect()` begins | `CONNECTION` | `INFO` | `"Connecting to ESP32…"` |
| `ConnectionViewModel` | connect succeeds | `CONNECTION` | `INFO` | `"Connected"` |
| `ConnectionViewModel` | connect fails / times out | `CONNECTION` | `ERROR` | `"Connect failed: <reason>"` |
| `ConnectionViewModel` | live link drops | `CONNECTION` | `ERROR` | `"Connection lost"` |
| `ConnectionViewModel` | permission denied | `APP` | `ERROR` | `"Connect failed: PERMISSION_DENIED"` |
| `ControlViewModel` | direction sent successfully | `SENT` | `INFO` | `"<DIRECTION> -> sent"` |
| `ControlViewModel` | direction dropped (not connected) | `SENT` | `WARN` | `"<DIRECTION> -> dropped (not connected)"` |

## Entity: DebugLog (the buffer itself)

| Attribute | Type | Notes |
|---|---|---|
| entries | `StateFlow<List<LogEntry>>` | Current buffer contents, oldest first (unchanged ordering contract from feature 004). |
| capacity | `Int` (constant, 50) | Unchanged from feature 004 — this feature doesn't require raising it. |

### Behavior (unchanged shape, new signature)

- `add(category: LogCategory, level: LogLevel = LogLevel.INFO, message: String)`: builds a
  `LogEntry` with the current timestamp, appends it, then truncates to the most recent 50 entries
  — same `takeLast(50)` mechanism as feature 004, just operating on `LogEntry` instead of `String`.
- No `clear()` / removal API — still out of scope.
- No persistence — still session-only (matches spec FR-014 / existing FR-008 from feature 004).

## Entity: Direction Control Press State (derived, not stored)

Unchanged from today's implementation: `ControlScreen`'s per-button `MutableInteractionSource` →
`collectIsPressedAsState()` already gives an accurate, real-time "is this control currently held"
signal per direction. This feature only changes what visual attributes are derived from it
(pressed background/shadow/icon-fill per the design tokens, disabled dim state when
`ConnectionState != Connected`) — no new state is introduced, and it stays local to each button
composable (see research.md Decision 6 for why this stays local rather than ViewModel-owned).

### State transitions

None beyond what already exists (`DebugLog` is still simple bounded append-only; `ConnectionState`
is unchanged; press state is unchanged). This feature only adds fields to what's captured at
existing transition points — it introduces no new state machine.
