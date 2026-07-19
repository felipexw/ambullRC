# Phase 1 Data Model: In-App Log Viewer Widget

## Entity: Log Entry

Represented as a plain `String` rendered as one line in the widget — no separate class/struct, per
Constitution Principle I (no abstraction beyond what's needed; a timestamp/severity/source struct
would go unused since the widget only ever renders the message text).

| Attribute | Type | Notes |
|---|---|---|
| message | String | Human-readable description of what happened. Order in the buffer is the only ordering signal (oldest first); no separate timestamp field is rendered, since relative order already satisfies FR-004/US2's "review the sequence of events" need. |

### Message content by source (satisfies FR-002)

| Source | Trigger | Example message |
|---|---|---|
| `ConnectionViewModel` | `connect()` begins | `"Connecting to ESP32…"` |
| `ConnectionViewModel` | connect succeeds | `"Connected"` |
| `ConnectionViewModel` | connect fails / times out | `"Connect failed: <reason>"` (reason mirrors existing `FailureReason` labels) |
| `ConnectionViewModel` | live link drops | `"Connection lost"` |
| `ControlViewModel` | tap sent successfully | `"<DIRECTION> -> sent"` |
| `ControlViewModel` | tap dropped (not connected / write failed) | `"<DIRECTION> -> dropped (not connected)"` |

## Entity: DebugLog (the buffer itself)

| Attribute | Type | Notes |
|---|---|---|
| entries | `StateFlow<List<String>>` | Current buffer contents, oldest first. Observed by the UI via `collectAsState()`. |
| capacity | `Int` (constant, 50) | Enforced on every `add()` — see research.md Decision 2. |

### Behavior

- `add(message: String)`: appends `message`, then truncates to the most recent 50 entries
  (`(current + message).takeLast(50)`).
- No `clear()` / removal API — out of scope per spec Assumptions (read-only widget for this iteration).
- No persistence — a fresh `DebugLog()` is constructed each time `MainActivity` (and therefore the
  process) starts; this satisfies FR-008 (session-only) with no extra code.

### State transitions

None beyond simple append — this is not a state machine, just a bounded append-only (per-slot) log.
