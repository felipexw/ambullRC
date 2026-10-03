# Horn Contract: Trigger + Local Cooldown

Android app, no network API. The contract is the extended `ControlViewModel` + `ControlScreen`
that the app and the tests bind to. Builds directly on
[003's command contract](../../003-send-direction-commands/contracts/command-contract.md) —
`Esp32Connection` is **not** changed by this feature at all (research.md); the horn reuses the
exact same `send` every other command already goes through. Unlike
[007's lights contract](../../007-lights-toggle-command/contracts/lights-contract.md), there is no
seam-level read-back — per the Clarifications session, the app never parses any ESP32-reported
horn state.

## `Esp32Connection` (seam) — unchanged

No changes. `connect()`, `awaitDisconnect()`, `disconnect()`, `send()`, `lightState` all remain
exactly as feature 007 left them.

## `ControlViewModel` — new members

```kotlin
private const val HORN_COOLDOWN_MS = 1500L

class ControlViewModel(
    private val connection: Esp32Connection,
    private val logger: DirectionLogger = AndroidDirectionLogger(),
    private val debugLog: DebugLog = DebugLog(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val hornCooldownMillis: Long = HORN_COOLDOWN_MS
) : ViewModel() {

    // Existing direction/lights members unchanged.

    /** true for hornCooldownMillis after a successfully sent trigger; false otherwise. Driven
     *  entirely by a local timer — never by any signal from the ESP32 (Clarifications). */
    val hornPlaying: StateFlow<Boolean>

    /** Whether the horn control currently accepts taps: true only while connected (the same
     *  signal setConnected already drives lightsEnabled from) AND hornPlaying is false. */
    val hornAvailable: StateFlow<Boolean>

    /** Sends exactly one "HORN\n" trigger over [connection], unless hornAvailable is false (not
     *  connected, or still within the cooldown window). On a successful send, starts (or
     *  restarts) the hornCooldownMillis timer that will flip hornPlaying back to false. Does
     *  nothing else — no ESP32 response of any kind is awaited or parsed. */
    fun onHornTapped()

    /** Called by the Activity whenever the connected/disconnected boolean it already computes for
     *  ControlScreen changes — same call site that already drives lightsEnabled. Additionally:
     *  transitioning to disconnected immediately cancels any pending cooldown and resets
     *  hornPlaying to false, so a later reconnect always starts fully available. */
    fun setConnected(connected: Boolean)
}
```

- **Guarantees** (asserted by unit tests, using `FakeEsp32Connection` + `kotlinx-coroutines-test`
  virtual time):
  - `setConnected(false)` → `hornAvailable == false` and `hornPlaying == false` immediately,
    regardless of any in-progress cooldown.
  - `setConnected(true)` with no prior tap → `hornAvailable == true`.
  - `onHornTapped()` while `hornAvailable == false` sends nothing (no `"HORN\n"` in
    `sentCommands`) and does not affect `hornPlaying` (FR-004, spec Edge Cases).
  - `onHornTapped()` while `hornAvailable == true` sends exactly one `"HORN\n"`; immediately after,
    `hornPlaying == true` and `hornAvailable == false`.
  - Advancing virtual time by exactly `hornCooldownMillis` after a successful tap flips
    `hornPlaying` back to `false` and `hornAvailable` (while still connected) back to `true`
    (FR-005). Advancing by less does not.
  - A second `onHornTapped()` call during the cooldown window sends nothing further — only one
    `"HORN\n"` total across any number of taps within one window (SC-004).
  - A `setConnected(false)` → `setConnected(true)` cycle occurring *during* a cooldown leaves the
    control fully available immediately on reconnect, not stuck waiting out the original window
    (research.md's stale-job fix).
  - Holding a directional control and tapping the horn produces both the repeating directional
    sends and the horn trigger, uninterrupted — the same already-proven concurrency guarantee
    feature 007 established for lights + directions, extended to a third concurrent command
    (FR-007).
  - Every `"HORN\n"` send outcome is also recorded in `debugLog`, matching the existing pattern for
    direction/lights commands.

## `ControlScreen` (Composable, stateless) — new button

```kotlin
@Composable
fun ControlScreen(
    viewModel: ControlViewModel,
    connected: Boolean,
    modifier: Modifier = Modifier
)
```

Signature is unchanged — the horn button reads `viewModel.hornPlaying`/`viewModel.hornAvailable`
internally, the same way `DirectionButton`s and `LightsButton` already read from `viewModel`.

- Renders one new button in the D-pad grid's bottom-right corner cell (row 3, column 2 — between
  `RIGHT` above it and `DOWN` beside it), replacing that cell's blank `Box` (research.md).
- Icon: `Icons.Filled.Campaign`, always the same glyph — tint/enabled-state (not the glyph)
  distinguishes the three statuses: Unavailable (dimmed `alpha`, same treatment the D-pad and
  lights button already use), Ready (`OnSurfaceVariant`, matching `DirectionButton`'s idle tint),
  Sounding (`Accent`, matching `DirectionButton`'s pressed/active tint).
- `testTag = "btn_horn"`, content description `"Horn"`.
- `enabled = viewModel.hornAvailable.collectAsState().value`; tapping calls
  `viewModel::onHornTapped`. Plain tap (`onClick`), not a press/release pair — the 1500ms cooldown
  is a fixed, app-owned duration, not tied to how long the button is held (spec.md Assumptions).

## `MainActivity` wiring

- No new `Esp32Connection` instance and no new field.
- No new wiring beyond what feature 007 already put in place — `ControlScreen`'s existing
  `LaunchedEffect(connected) { viewModel.setConnected(connected) }` already covers gating (and now
  also resetting) the horn too, since `hornAvailable` derives from the same `connected` signal
  `lightsEnabled` does.

## Out of scope (per spec Assumptions)

- No audio data, codec, or Bluetooth audio profile of any kind.
- No press-and-hold — a plain tap, gated by `hornAvailable`.
- No ESP32 read-back of any kind — `hornPlaying` is a pure local timer, never confirmed by the
  ESP32 (Clarifications).
- No persistence of `hornPlaying` across app restarts.
- No new dependency — `Icons.Filled.Campaign` is already present in the `material-icons-extended`
  dependency added by feature 007.
