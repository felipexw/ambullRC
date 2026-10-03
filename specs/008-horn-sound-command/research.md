# Research: Horn Sound Command (Played by the ESP32)

Following the `/speckit-clarify` decision to use a fixed local timer instead of any ESP32-reported
completion signal, this feature no longer touches `Esp32Connection`/`BluetoothEsp32Connection` at
all — it needs no new seam, no new interface property, and no change to the shared reader loop.
The only real decision left is how the local cooldown timer itself should be implemented in
`ControlViewModel`. Everything else directly reuses already-decided architecture (feature 003's
`send`, feature 007's `setConnected` gating pattern).

## Decision: Purely local `ControlViewModel` state — no `Esp32Connection` changes at all

**Decision**: `Esp32Connection`, `BluetoothEsp32Connection`, and `FakeEsp32Connection` are
untouched by this feature. `ControlViewModel` alone tracks a `_hornPlaying: MutableStateFlow<Boolean>`
and derives `hornAvailable` from it plus the existing `connected` signal already driving
`lightsEnabled`. `onHornTapped()` calls the existing `connection.send("HORN\n")` — the same method
signature every other command already uses — and needs nothing new from the seam.

**Rationale**: Per the Clarifications session, the app deliberately does not read back any
horn-related state from the ESP32. With no inbound signal to recognize, there is nothing for the
shared reader loop (feature 007) to parse and nothing for `Esp32Connection` to expose — extending
it would be pure unused surface area. This makes the feature architecturally closer to feature
003's one-way direction commands than to feature 007's polled/confirmed lights toggle: send a
command, and locally track "how long ago" rather than "what came back."

**Alternatives considered**:
- *Still add `Esp32Connection.hornPlaying` but drive it from a local timer inside
  `BluetoothEsp32Connection` instead of `ControlViewModel`*: rejected — this is pure UI/interaction
  state (how long to disable a button after a tap), not a fact about the Bluetooth link itself; it
  belongs in the ViewModel, exactly where `pressedDirections`/`repeatJobs` already live for the
  same reason.

## Decision: A single tracked reset `Job`, cancelled and restarted on each successful trigger

**Decision**: `ControlViewModel` keeps one `private var hornResetJob: Job?`. On a successful send,
it sets `_hornPlaying.value = true`, cancels any previous `hornResetJob` (defensive — see below),
and launches a new one: `viewModelScope.launch { delay(1500L); _hornPlaying.value = false }`.
`setConnected(false)` also cancels `hornResetJob` and immediately resets `_hornPlaying.value =
false`.

**Rationale**: Because a new trigger can only be sent while `hornAvailable` is true, and
`hornAvailable` is false whenever `hornPlaying` is true, at most one reset job is ever "supposed"
to be pending at a time — cancelling defensively before creating a new one is a correctness
safety net, not something normally reachable, and costs nothing. The `setConnected(false)` reset is
not defensive, though — it fixes a real bug: without it, a disconnect mid-cooldown followed by a
fast reconnect-and-retap could leave the *old* pending job to fire later and prematurely cut the
*new* cooldown short (turning a 1500ms window into something shorter than intended). Cancelling
and resetting on disconnect makes every reconnect start from a clean, fully-available state,
which is also exactly what the spec's Edge Cases section asks for ("the app MUST treat the horn as
no longer sounding" on disconnect).

**Alternatives considered**:
- *No cancellation, just let stale jobs fire harmlessly*: rejected — as shown above, this is not
  actually harmless once a disconnect/reconnect happens mid-cooldown; it's a real (if narrow) race
  that a couple of lines avoids entirely.
- *Use `System.currentTimeMillis()`/timestamp comparison instead of a delayed coroutine*: works too,
  but would need to be checked from somewhere (e.g. polled), whereas a single `delay`-based job is
  a direct, push-style fit for `StateFlow` and matches how every other timed behavior in this
  ViewModel (the 100ms directional repeat loop) is already expressed — a coroutine `delay`, not a
  polled clock read.

## Decision: `HORN_COOLDOWN_MS = 1500L`, a hardcoded constant

**Decision**: The 1500ms figure from the Clarifications session is a `private const val
HORN_COOLDOWN_MS = 1500L` alongside the existing `REPEAT_INTERVAL_MS` constant in
`ControlViewModel.kt` — not exposed as a constructor parameter, not configurable.

**Rationale**: The user specified this exact value as a requirement (FR-003/004/005), not as a
tunable planning detail — matching how `REPEAT_INTERVAL_MS` (100ms) is already a hardcoded
constant in the same file rather than a constructor parameter. Principle I: no configuration
surface for a value with no current requirement to vary it.

**Alternatives considered**: *Constructor parameter with a 1500ms default (like
`lightsPollIntervalMillis` in feature 007)*: feature 007 made its poll interval a parameter because
tests needed to advance virtual time against it conveniently; the same reasoning applies here too,
so this is in fact adopted — see the contract below — but purely for test ergonomics, not because
the value is meant to be product-configurable.

## Decision: Icon reuses the already-included `material-icons-extended` dependency — no new dependency

**Decision**: Unchanged from earlier rounds — the horn button uses `Icons.Filled.Campaign`,
confirmed present in the `material-icons-extended` dependency already added for feature 007's
lights icons.

**Rationale**: A UI-only choice, independent of every audio/protocol correction so far.

## Decision: Horn button occupies the D-pad grid's bottom-right corner cell

**Decision**: Unchanged from earlier rounds — row 3 (bottom row), column 2, directly right of
`DOWN` and directly below `RIGHT`, mirroring the lights button's placement in the opposite bottom
corner (row 3, column 0, feature 007).

**Rationale**: Also a UI-only layout choice, independent of the audio/protocol corrections — see
the original reasoning (symmetry with the lights button, both flanking `DOWN`, no new layout
mechanism).
