# Quickstart & Validation: Lights Toggle Command

How to build, run, and validate the lights toggle+poll round trip. Implementation details live in
tasks.md / the source; this is a run-and-verify guide. Builds on
[003's quickstart](../003-send-direction-commands/quickstart.md) — the app must already auto-connect
and send direction commands before lights can be layered on top.

## Prerequisites

- Everything in [003's quickstart prerequisites](../003-send-direction-commands/quickstart.md#prerequisites).
- The ESP32 sketch must, for this feature's on-device check, actually implement the three-message
  protocol from [contracts/lights-contract.md](./contracts/lights-contract.md):
  - On receiving `LIGHTS_ON` / `LIGHTS_OFF`, switch its lights accordingly.
  - On receiving `LIGHTS?`, reply with a line reading exactly `LIGHT_ON` or `LIGHT_OFF` (its
    current actual state) followed by a newline.
  - This app-side feature does not implement or ship any ESP32 firmware — see spec.md Assumptions.

## Build

```bash
./gradlew :app:assembleDebug
```

Expected: build succeeds, pulling in the one new dependency
(`androidx.compose.material:material-icons-extended`, added for the lightbulb icon pair — see
research.md).

## Run the app (on a physical phone with the ESP32)

```bash
./gradlew :app:installDebug
```

Validate against the spec:

- **US1 / SC-001**: Let the app auto-connect. Tap the lights button (bottom-left corner of the
  D-pad, between Left and Down). Confirm `LIGHTS_ON` (or `LIGHTS_OFF`, depending on starting state)
  arrives at the ESP32, the RC's actual light turns on/off, and the button's icon flips to match
  within about half a second (the poll interval).
- **US1 / FR-002/FR-003**: Watch the icon closely on tap — it should **not** flip the instant you
  tap; it should only flip once the next poll response confirms it. Confirm via the in-app debug log
  (feature 004) that a `LIGHTS?` query is going out roughly twice a second while connected.
- **US1 / FR-006**: Hold a directional button and tap the lights button mid-hold. Confirm the
  directional command keeps arriving at its normal 100ms cadence with no visible pause, and the
  lights command also arrives.
- **US2 / FR-005, FR-008**: Power off the ESP32 (or leave the app "Not connected"). Confirm the
  lights button is dimmed/disabled and tapping it does nothing. Power the ESP32 back on, **Retry**
  to reconnect, and confirm the button stays disabled (showing the last-known icon) until the first
  fresh poll response arrives, then re-enables.

```bash
# Optional: watch app-side lights logs
adb logcat | grep -i ambullrc
```

## Automated validation

### Unit tests (JVM — ViewModel logic, no hardware)

```bash
./gradlew :app:testDebugUnitTest
```

`ControlViewModelTest` gains lights coverage against `FakeEsp32Connection`, using
`kotlinx-coroutines-test` virtual time to advance through poll intervals: `setConnected(true)` starts
polling and `lightsEnabled` flips only once `lightState` is pushed non-null (FR-005/FR-007/FR-008);
`onLightsTapped()` sends the opposite of the current `lightsOn` and never changes `lightsOn` itself
(FR-002/FR-003); `setConnected(false)` stops polling without resetting `lightsOn`; a held direction
plus a lights tap send both without interruption (FR-006). See
[contracts/lights-contract.md](./contracts/lights-contract.md).

### Instrumented UI test (device/emulator)

```bash
./gradlew :app:connectedDebugAndroidTest
```

`ControlScreenTest` is extended to assert the lights button's position, icon swap between
`lightsOn` values, and disabled appearance while `lightsEnabled == false`.

## Definition of done (constitution Principle V)

- [ ] `:app:testDebugUnitTest` passes (`ControlViewModel` lights coverage + `FakeEsp32Connection`).
- [ ] `:app:connectedDebugAndroidTest` passes (lights button rendering/state).
- [ ] On-device smoke check against a real ESP32 running the `LIGHTS_ON`/`LIGHTS_OFF`/`LIGHTS?`
      protocol: tap toggles the real light, icon updates within one poll interval (not
      instantly), and the button correctly disables/re-enables across a disconnect/reconnect.
