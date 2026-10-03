# Data Model: Horn Sound Command (Played by the ESP32)

No persistence (nothing about horn state survives an app restart, same as every other control in
this app). The "data" here is purely local `ControlViewModel` state — there is no ESP32-reported
signal involved at all (per Clarifications). See
[contracts/horn-contract.md](./contracts/horn-contract.md) for exact signatures.

## Entity: Horn Cooldown State

The single piece of new state this feature introduces — whether the app is currently within the
1500ms window since the last successfully sent trigger.

| Field | Type | Meaning |
|-------|------|---------|
| `playing` | `Boolean` | `true` = fewer than 1500ms have elapsed since the last successful `"HORN\n"` send; `false` = idle/available. |

- **Held where**: `ControlViewModel`'s own `hornPlaying: StateFlow<Boolean>` — there is no
  seam-level counterpart; `Esp32Connection` is untouched by this feature (research.md).
- **Lifecycle**:
  1. `false` at ViewModel construction.
  2. Becomes `true` the instant `connection.send("HORN\n")` returns `true` (a successful send) —
     never for a failed send (spec.md Edge Cases).
  3. Becomes `false` automatically 1500ms later, via a tracked coroutine `Job`
     (`hornResetJob`) — entirely time-based, with no dependency on anything the ESP32 does or
     reports (FR-003).
  4. Also forced back to `false` immediately if `setConnected(false)` is called (disconnect),
     cancelling any pending reset job so a later reconnect always starts clean (research.md).
- **Invariants**:
  - Only ever `true`/`false`.
  - Exactly one reset job is ever pending at a time (research.md).

## Entity: Horn Control Availability (derived, not stored independently)

What the app actually shows/enables for the horn control (FR-006). Computed from the existing
`connected` boolean (already flowing into `ControlViewModel.setConnected`) and the cooldown state
above — no new persisted field:

| `connected` | `hornPlaying` | Displayed status | Tappable? |
|-------------|----------------|-------------------|-----------|
| `false` | (any) | Unavailable | No |
| `true` | `false` | Ready | Yes |
| `true` | `true` | Sounding | No |

Identical in shape to the `lightsEnabled`/`connected` derivation (feature 007), just with
`hornPlaying` (a local timer) standing in for what would otherwise be a second seam-level signal.

## Operations

No `Esp32Connection` interface changes. `ControlViewModel` reuses the existing `send` exactly as
direction/lights commands already do:

- `suspend fun send(message: String): Boolean` — unchanged signature, reused for the one new
  outbound message (`"HORN\n"`).

### State dependency

| Underlying condition | `hornPlaying` behavior |
|------------------------|------------------------|
| ViewModel just constructed, never tapped | `false` |
| `"HORN\n"` send succeeds | `true` |
| `"HORN\n"` send fails (not connected) | unchanged (stays whatever it was — a failed send never starts a cooldown) |
| 1500ms elapse after a successful send, with no disconnect in between | `false` |
| `setConnected(false)` is called at any point | immediately `false`, and any pending reset is cancelled |

No new state machine is introduced beyond this; it exists entirely alongside — not layered onto —
the existing `ConnectionState` machine (feature 002) and `lightState`/`lightsOn` pair (feature 007).
