# Lights Contract: Toggle + Poll the RC's Lights

Android app, no network API. The contract is the extended Bluetooth seam + `ControlViewModel` +
`ControlScreen` that the app and the tests bind to. Builds directly on
[003's command contract](../../003-send-direction-commands/contracts/command-contract.md) —
`Esp32Connection` gains one property, nothing else in that contract changes.

## `Esp32Connection` (seam) — new state

```kotlin
interface Esp32Connection {
    suspend fun connect()          // CHANGED: also resets lightState to null, see below
    suspend fun awaitDisconnect()  // CHANGED implementation only: parses lines instead of
                                    // discarding bytes; same external contract (resumes on drop)
    fun disconnect()               // unchanged, from feature 002
    suspend fun send(message: String): Boolean  // unchanged, from feature 003

    /** The ESP32's most recently confirmed lights on/off state, or null if no confirmation has
     *  been received since the current connection attempt began. Never set directly by the app —
     *  only updated when the shared reader loop parses a recognized inbound line. Reset to null at
     *  the start of every connect(). */
    val lightState: StateFlow<Boolean?>
}
```

- **Real implementation** `BluetoothEsp32Connection` (in `data/`): `connect()` sets
  `_lightState.value = null` before opening the socket. `awaitDisconnect()`'s loop now wraps
  `inputStream` in a `BufferedReader` and calls `readLine()` instead of reading raw bytes into a
  discard buffer; each line is compared against `"LIGHT_ON"`/`"LIGHT_OFF"` (exact match) and, if
  recognized, updates `_lightState.value`; any other line (including partial/garbled input) is
  ignored. The loop still ends — and `awaitDisconnect()` still returns — on `null` from `readLine()`
  (EOF) or `IOException`, exactly as before.
- **Test implementation** `FakeEsp32Connection` (in `test`/`androidTest`): gains a mutable
  `lightState: MutableStateFlow<Boolean?>` a test can push values into directly (simulating a
  recognized inbound line, with no fake serial parsing needed), reset to `null` inside `connect()`.
  `sentCommands` (already existing) records `"LIGHTS_ON\n"`/`"LIGHTS_OFF\n"`/`"LIGHTS?\n"` writes the
  same way it already records direction commands — no new tracking field needed.

## `ControlViewModel` (AndroidX `ViewModel`) — new members

```kotlin
class ControlViewModel(
    private val connection: Esp32Connection,
    private val logger: DirectionLogger = AndroidDirectionLogger(),
    private val debugLog: DebugLog = DebugLog(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val lightsPollIntervalMillis: Long = 500L
) : ViewModel() {

    // Existing direction members unchanged: onDirectionPressed/onDirectionReleased.

    /** The ESP32's last-confirmed lights state; false ("off") until the first confirmation. Only
     *  ever changes in response to a confirmed poll response — never as a direct effect of
     *  onLightsTapped(). */
    val lightsOn: StateFlow<Boolean>

    /** Whether the lights control currently accepts taps: true only while connected AND at least
     *  one light-state confirmation has been received since the last setConnected(true). */
    val lightsEnabled: StateFlow<Boolean>

    /** Called by the Activity whenever the connected/disconnected boolean it already computes for
     *  ControlScreen changes. true starts lights polling (resets lightsEnabled to false until the
     *  first confirmation); false stops polling and disables the control, leaving lightsOn at its
     *  last value. */
    fun setConnected(connected: Boolean)

    /** Sends exactly one toggle command — the opposite of lightsOn's current value — over
     *  [connection]. A no-op (no command sent) if lightsEnabled is false. Does NOT change
     *  lightsOn itself; that only happens once a subsequent poll confirms the new state. */
    fun onLightsTapped()
}
```

- **Guarantees** (asserted by unit tests, using `kotlinx-coroutines-test` virtual time and
  `FakeEsp32Connection`):
  - `setConnected(true)` starts sending `"LIGHTS?\n"` every `lightsPollIntervalMillis`; `lightsEnabled`
    stays `false` until `connection.lightState` first becomes non-null, then flips to `true` and
    `lightsOn` takes that value (FR-005, FR-007, FR-008).
  - `setConnected(false)` stops the poll and sets `lightsEnabled` to `false`; `lightsOn` is left
    unchanged (Edge Cases: last-known state persists across a drop).
  - `onLightsTapped()` while `lightsEnabled == false` sends nothing and does not change `lightsOn`
    (SC-004-equivalent for lights).
  - `onLightsTapped()` while `lightsEnabled == true` sends exactly one of `"LIGHTS_ON\n"`/
    `"LIGHTS_OFF\n"` — the opposite of the current `lightsOn` value — and `lightsOn` does **not**
    change until `connection.lightState` reports that new value on a later poll (FR-002, FR-003).
  - Repeated taps before any poll response arrives each send a command for whatever `lightsOn`
    still reads at that moment (Edge Cases: rapid taps).
  - Holding a directional control and tapping the lights control produces both the repeating
    directional sends and the lights send, uninterrupted (FR-006).
  - Every `"LIGHTS_ON\n"`/`"LIGHTS_OFF\n"`/`"LIGHTS?\n"` send outcome is also recorded in `debugLog`,
    matching the existing pattern for direction commands.

## `ControlScreen` (Composable, stateless) — new button

```kotlin
@Composable
fun ControlScreen(
    viewModel: ControlViewModel,
    connected: Boolean,
    modifier: Modifier = Modifier
)
```

Signature is unchanged — the lights button reads `viewModel.lightsOn`/`viewModel.lightsEnabled`
internally, the same way `DirectionButton`s already read from `viewModel`.

- Renders one new button in the D-pad grid's bottom-left corner cell (row 3, column 0 — between
  `LEFT` above it and `DOWN` beside it), replacing that cell's blank `Box` (FR-001).
- Icon: `Icons.Filled.Lightbulb` when `lightsOn == true`, `Icons.Outlined.Lightbulb` when `false` —
  never the state a tap would produce, always the last confirmed one (FR-004).
- `testTag = "btn_lights"`, content description `"Lights"`.
- `enabled = viewModel.lightsEnabled.collectAsState().value`; tapping calls
  `viewModel::onLightsTapped`. Same dimmed/disabled visual treatment (`alpha`) the directional
  buttons already use when disabled — this is the enabled/disabled signal FR-004 requires to stay
  visually distinct from the on/off icon change.
- Unlike `DirectionButton`, this is a plain tap (`onClick`), not a press/release pair — lights don't
  need the hold-to-repeat behavior directional commands use (spec.md Assumptions).

## `MainActivity` wiring (extends feature 003's wiring)

- No new `Esp32Connection` instance — the existing single shared `BluetoothEsp32Connection` now also
  carries `lightState`.
- No new wiring needed beyond the existing `connected` computation already passed into
  `ControlScreen` — `ControlScreen` itself forwards it to `controlViewModel.setConnected(connected)`
  via a `LaunchedEffect(connected)` (implementation refinement: since `ControlScreen` already owns
  forwarding `connected` to the D-pad's enable/disable state, forwarding it to the ViewModel there
  too — rather than in `MainActivity` — keeps that responsibility in one place and lets
  `ControlScreenTest` exercise it directly). No new `StateFlow` is shared between the two
  ViewModels.

## Out of scope (per spec Assumptions)

- No request/response correlation between a given `"LIGHTS?\n"` write and the line that answers it —
  the app only cares about the latest confirmed value.
- No persistence of `lightsOn` across app restarts.
- No configurable poll interval.
- No new commands beyond the three listed (`LIGHTS_ON`, `LIGHTS_OFF`, `LIGHTS?`).
