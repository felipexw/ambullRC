# Data Model: Lights Toggle Command

No persistence (spec.md Assumptions: nothing about light state survives an app restart). The "data"
here is the confirmed light state itself and the small set of wire messages that produce/report it.
See [contracts/lights-contract.md](./contracts/lights-contract.md) for exact signatures.

## Entity: Light State

The single piece of new state this feature introduces — the ESP32's own on/off state for its
lights, as most recently confirmed over Bluetooth.

| Field | Type | Meaning |
|-------|------|---------|
| `on` | `Boolean` | `true` = lights on, `false` = lights off. |

- **Held where**: `BluetoothEsp32Connection.lightState: StateFlow<Boolean?>` (seam-level, `null` =
  never confirmed since the current connection attempt began) and `ControlViewModel.lightsOn:
  StateFlow<Boolean>` (ViewModel-level, always a concrete `Boolean`, defaulting to `false`/"off"
  until the first confirmation arrives — per FR-008, the UI never shows a null/unknown icon state).
- **Lifecycle**:
  1. `null` at the start of every `connect()` attempt (Research: "lightState resets to null").
  2. Becomes a concrete value the first time the shared reader loop parses a recognized
     `"LIGHT_ON"`/`"LIGHT_OFF"` line (FR-007).
  3. Stays at its last concrete value across further polls that don't change it, across a
     disconnect (Edge Cases: "keeps showing the last confirmed state until reconnected"), and across
     write failures — it only ever changes on a genuinely recognized inbound line.
  4. Never reset by the app itself outside of step 1; not persisted, so an app restart starts fresh.
- **Invariants**:
  - Only ever `true`/`false`/(`null` at seam level only, before first confirmation).
  - Changes only in response to an ESP32-reported line, never as a direct effect of the app's own
    toggle tap (FR-002/FR-003 — confirmed-only, not optimistic).

## Entity: Lights Control Enablement (derived, not stored independently)

Whether the lights button currently accepts taps. Not a new stored field — it is computed from two
existing signals:

| Condition | `lightsEnabled` |
|-----------|-----------------|
| App disconnected (`ControlViewModel.setConnected(false)`) | `false` |
| Just connected, no light-state confirmation received yet this connection | `false` |
| A `"LIGHTS?\n"` query write itself failed | `false` |
| Connected AND at least one confirmation received since the last connect | `true` |

This mirrors the existing `connected: Boolean` gate the directional buttons already use
(`ControlScreen`/`DirectionButton`), extended with the one additional "confirmed at least once"
condition FR-005/FR-008 require.

## Operations

Extends the existing seam interface (feature 003) with one new property and reuses the existing
`send`:

- `val lightState: StateFlow<Boolean?>` — new. See lifecycle above.
- `suspend fun send(message: String): Boolean` — unchanged signature, reused for all three new
  outbound messages (`"LIGHTS_ON\n"`, `"LIGHTS_OFF\n"`, `"LIGHTS?\n"`); behaves exactly as it already
  does for direction commands (FR-002/FR-007 both go through this one existing method).

### State dependency

| Underlying link state | `lightState` behavior |
|------------------------|------------------------|
| No `connect()` has succeeded yet this attempt | `null` |
| Connected, no recognized response line seen yet | `null` |
| Connected, at least one `"LIGHT_ON"`/`"LIGHT_OFF"` line seen | that line's value |
| A fresh `connect()` begins (including a reconnect after a drop) | reset to `null` |

No new state machine is introduced beyond this; it is a small addition alongside the existing
`ConnectionState` machine (feature 002), not a replacement or parallel copy of it.
