# Research: Lights Toggle Command

Two real technical unknowns exist beyond the usual "extend the existing seam" pattern used by prior
features: (1) the app has never read anything back from the ESP32 before, and the one place that
already reads the socket (`awaitDisconnect`'s discard loop) would otherwise collide with a second
reader; (2) there is no light-bulb icon in the icon set the project currently depends on. Both are
resolved below. Everything else extends already-decided architecture (feature 003's `Esp32Connection`
seam, feature 005's `ControlViewModel`/`ControlScreen` split).

## Decision: One shared reader loop, not two competing readers

**Decision**: `BluetoothEsp32Connection.awaitDisconnect()`'s existing loop — which currently just
discards every byte it reads until EOF/`IOException` — is changed to read line-by-line
(`BufferedReader.readLine()`) instead of raw bytes. Each line is checked against the two recognized
light-state responses (`"LIGHT_ON"`, `"LIGHT_OFF"`); a recognized line updates a new
`lightState: StateFlow<Boolean?>` exposed by the seam. Anything else is ignored, exactly as raw bytes
were ignored before. The loop still ends (and `awaitDisconnect()` still returns) on EOF/`IOException`,
unchanged.

**Rationale**: A `BluetoothSocket`'s `InputStream` has exactly one reasonable consumer at a time —
two coroutines both calling `read()` on it would race and corrupt line framing. `awaitDisconnect()` is
already the app's only stream reader, already running for the entire connected lifetime, and already
built to survive "read something, discard it, keep going" — turning "discard" into "parse, then
discard the ones I don't recognize" is a small, local change that adds the new capability without
adding a second reader or changing `awaitDisconnect`'s external contract (still resumes on
disconnect, same as feature 002/003). This is the smallest change that satisfies FR-007 without
regressing existing disconnect-detection latency (Connected → Failed(CONNECTION_LOST) still fires the
instant the socket actually closes, not just at the next poll tick).

**Alternatives considered**:
- *Open a second `InputStream` reader dedicated to light-state responses*: not possible — a
  `BluetoothSocket` exposes one `InputStream`; a second concurrent reader would race the first.
- *Replace `awaitDisconnect`'s blocking read with periodic polling reads*: rejected — would make
  disconnect detection latency depend on the lights poll interval, a regression for every existing
  feature (002/003) that relies on near-instant `CONNECTION_LOST` reporting.
- *Have the ESP32 push light-state on every change instead of the app polling*: rejected by the
  clarification session — the user explicitly chose continuous app-side polling over a push model.

## Decision: Plain newline-delimited text, three new outbound messages + two recognized inbound lines

**Decision**: Reusing the exact framing convention from feature 003 (plain text, `\n`-delimited,
UTF-8):
- Outbound (app → ESP32), sent via the existing `Esp32Connection.send`: `"LIGHTS_ON\n"`,
  `"LIGHTS_OFF\n"` (toggle request) and `"LIGHTS?\n"` (state query, sent once per poll tick).
- Inbound (ESP32 → app), recognized by the shared reader loop above: `"LIGHT_ON"`, `"LIGHT_OFF"`
  (the `readLine()` call already strips the line terminator).

**Rationale**: Matches the constitution's "small fixed-size message or simple delimited text"
requirement and feature 003's precedent exactly — no new encoding, no shared constants module, no
request/response correlation IDs. The query and its eventual answer are deliberately *not* matched
by a request ID: the poll loop just re-sends `"LIGHTS?\n"` on a fixed interval and separately observes
`lightState` for whatever answer eventually arrives, which is simpler than tracking in-flight
requests and is enough to satisfy FR-007 (the ESP32 is the source of truth; the app only cares about
the latest value, not which query triggered it).

**Alternatives considered**:
- *Correlate each query with its response (e.g. sequence numbers)*: rejected — no requirement in the
  spec for this, and it reintroduces exactly the kind of protocol machinery Principle I says to avoid
  when a simpler thing works.
- *One combined message rewritten as the direction commands' pattern (`"LIGHTS_ON"`/`"LIGHTS_OFF"`
  reused as both the toggle command and, echoed back, the state report)*: rejected — conflating
  "the command I'm sending" and "the state I'm being told" in one string is exactly the kind of
  implicit coupling this project's existing contracts avoid (see the direction-command contract's
  explicit input/output message tables).

## Decision: Confirmed-only state lives in `ControlViewModel`, gated by the same boolean the View already computes

**Decision**: No new `LightsViewModel` and no new inter-ViewModel `StateFlow` wiring.
`ControlViewModel` gains a `setConnected(connected: Boolean)` method; `MainActivity` calls it from a
`LaunchedEffect(connected)` alongside the existing `connected` boolean it already computes from
`connectionViewModel.state` for `ControlScreen`. `ControlViewModel` exposes `lightsOn: StateFlow<Boolean>`
(default `false`) and `lightsEnabled: StateFlow<Boolean>` (default `false`) for `ControlScreen` to
render the new button from.

**Rationale**: The lights button lives on the same control screen as the D-pad and must combine
freely with held directional commands (FR-006) — that is squarely `ControlViewModel`'s existing job.
Introducing a second ViewModel for one button would duplicate the connected/disconnected gating logic
that already exists once, for no benefit (Principle I). Passing just the `Boolean` the Activity
already derives (rather than the whole `ConnectionState` or a shared `StateFlow` reference) keeps
`ControlViewModel` exactly as decoupled from `ConnectionViewModel` as it is today — it still never
imports or reads `ConnectionViewModel`, only the pre-existing `ConnectionState`-derived boolean.

**Alternatives considered**:
- *New dedicated `LightsViewModel`*: rejected — would need the same connected-gating logic
  `ControlViewModel` already has, plus its own wiring in `MainActivity`, for a single button.
- *`ControlViewModel` takes `connectionViewModel.state: StateFlow<ConnectionState>` directly in its
  constructor*: rejected — couples `ControlViewModel` to another ViewModel's type/instance instead of
  the plain boolean the View already needed to compute anyway.

## Decision: `lightState` resets to `null` at the start of every `connect()`

**Decision**: `BluetoothEsp32Connection.lightState` (the new `StateFlow<Boolean?>`) is reset to `null`
as the first step of `connect()`, before the socket is opened.

**Rationale**: `ControlViewModel` subscribes to `lightState` once, for its whole lifetime (not
per-reconnect), so a plain `StateFlow` would otherwise hand a fresh subscriber whatever value was
last confirmed *before* a drop — which would incorrectly re-enable the lights control on reconnect
before a genuinely fresh confirmation arrives, violating FR-005/FR-008 ("disabled... whenever a
light-state query has not yet succeeded (first connect)"). Resetting at `connect()` time guarantees
every new connection attempt starts unconfirmed, with no extra bookkeeping (no "connection epoch"
counter, no re-subscribing per connect) in `ControlViewModel`.

**Alternatives considered**:
- *Re-subscribe to `lightState` on every `setConnected(true)` call*: works too, but requires
  `ControlViewModel` to distinguish "value already there when I subscribed" from "value that arrived
  after," which `StateFlow` doesn't do — would need a different primitive (e.g. `Channel`/`SharedFlow`)
  purely to solve a problem the reset-on-connect approach avoids entirely.

## Decision: Poll every 500ms

**Decision**: The lights state query (`"LIGHTS?\n"`) is sent once every 500ms while connected — a
new, separate cadence from the existing 100ms directional-repeat interval.

**Rationale**: SC-001 bounds the icon's update lag to "one polling interval" after a tap; 500ms is
fast enough that the lag is barely noticeable for a toggle switch (unlike steering/throttle, lights
don't need 100ms-grade responsiveness — Assumptions in spec.md already note this isn't a
safety-critical stream) while keeping the added Bluetooth traffic to roughly a fifth of the existing
directional-repeat traffic per held button. This is a planning-time value, not a product requirement
(per spec.md's Assumptions), and can be tuned freely later without a spec change.

**Alternatives considered**:
- *Match the 100ms directional cadence*: rejected — five times the traffic for a control that has no
  100ms-grade responsiveness requirement.
- *Make it configurable*: rejected outright by Principle I — no current requirement for that.

## Decision: Add `material-icons-extended`, scoped to the `Lightbulb` icon pair

**Decision**: Add `androidx.compose.material:material-icons-extended` and use
`Icons.Filled.Lightbulb` (lights on) / `Icons.Outlined.Lightbulb` (lights off) for the new button's
icon.

**Rationale**: The project's current icon dependency, `material-icons-core`, only bundles a small
curated subset (confirmed by inspecting the shipped `.aar`: arrows, home, search, star, etc. — no
lightbulb, flash, or power-related icon of any kind). There is no idiomatic Compose/Material3 way to
get a recognizable "light" glyph without either adding this dependency or hand-drawing a custom
vector icon. Per the Development Workflow guidance ("adopt a new dependency only when the task
genuinely cannot be done reasonably without it"), a semantically-correct, instantly recognizable
on/off bulb pair is exactly what a "delightful UX" (Principle IV) toggle control needs, and no
reasonable substitute exists in the already-included set. `material-icons-extended` is a large `.aar`,
but this is the same category of one-time, narrowly-scoped addition the project already made for
`material-icons-core` (per CLAUDE.md's existing note on that dependency) — it is not a new
architectural layer, just more icon glyphs from the same icon family already in use.

**Alternatives considered**:
- *Hand-draw a custom `ImageVector` bulb shape*: rejected — this is exactly the "custom rendering...
  built solely to support visual effects" Principle IV explicitly says to avoid in favor of standard
  primitives; a hand-drawn vector is more code to maintain than one dependency line for a standard,
  recognizable icon.
- *Reuse an unrelated core icon (e.g. `Star`/`StarBorder` or `Favorite`/`FavoriteBorder`) as a stand-in
  for on/off*: rejected — fails the "instantly recognizable" bar for a lights control and would
  actively confuse the single existing user of this app.
- *Use a plain colored dot/shape instead of a semantic icon*: rejected — the spec explicitly asked for
  an icon that changes with state (Clarifications, Q1), and a bare colored dot duplicates the
  enabled/disabled dimming treatment already used elsewhere, which FR-004 requires to stay visually
  distinct from the on/off icon change.

## Decision: Lights button occupies the bottom-left corner cell of the D-pad grid

**Decision**: In `ControlScreen`'s 3×3 grid, the currently-blank `Box(Modifier.size(cellSize))` at
row 3 (bottom row), column 0 — directly below `LEFT` and directly left of `DOWN` — becomes the lights
button.

**Rationale**: FR-001 requires the control to sit "between the Down and Left directional controls."
Of the grid's four blank corner cells, only this one is adjacent to both `LEFT` (directly above) and
`DOWN` (directly to its right) simultaneously — the literal geometric "between" the feature
description and spec both ask for.

**Alternatives considered**: The other three blank corners (top-left, top-right, bottom-right) are
each adjacent to only one directional button, not both `Down` and `Left` — none satisfy FR-001.
