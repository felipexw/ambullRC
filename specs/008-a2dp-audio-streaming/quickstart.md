# Quickstart & Validation: Horn Sound Command (Played by the ESP32)

How to build, run, and validate the horn trigger + local cooldown. Implementation details live in
tasks.md / the source; this is a run-and-verify guide. Builds on
[003's quickstart](../003-send-direction-commands/quickstart.md) and
[007's quickstart](../007-lights-toggle-command/quickstart.md) — the app must already auto-connect,
send direction commands, and toggle lights before the horn can be layered on top.

## Prerequisites

- Everything in [003's quickstart prerequisites](../003-send-direction-commands/quickstart.md#prerequisites).
- The ESP32 sketch must, for this feature's on-device check, react to a `HORN` line by playing its
  own built-in horn/siren sound. It does **not** need to send anything back — this app-side
  feature never reads any horn-related response (see spec.md Clarifications). This app-side
  feature does not implement or ship any ESP32 firmware or sound asset.

## Build

```bash
./gradlew :app:assembleDebug
```

Expected: build succeeds. No new dependency is added — `Icons.Filled.Campaign` is already present
in the `material-icons-extended` dependency (feature 007), and `Esp32Connection` is unchanged.

## Run the app (on a physical phone with the ESP32)

```bash
./gradlew :app:installDebug
```

Validate against the spec:

- **US1 / SC-001**: Let the app auto-connect. Tap the horn button (bottom-right corner of the
  D-pad, between Right and Down). Confirm `HORN` arrives at the ESP32 and it sounds its horn.
  Confirm the button visually shows "sounding" for about 1.5 seconds, then automatically returns
  to normal — with no dependency on anything coming back from the ESP32.
- **US1 / FR-007**: Hold a directional button and tap the horn mid-hold. Confirm the directional
  command keeps arriving at its normal 100ms cadence with no visible pause, and the horn trigger
  also arrives — the same concurrency already proven for lights + directions (feature 007).
- **US2 / FR-004, SC-004**: Tap the horn button, then tap it again several times within the next
  ~1.5 seconds. Confirm via the in-app debug log (feature 004) that only the first tap's `HORN`
  line actually goes out; a tap sent after the cooldown elapses does send a new `HORN`.
- **US3 / FR-006, FR-008**: Power off the ESP32 (or leave the app "Not connected"). Confirm the
  horn button is dimmed/disabled. While a cooldown is in progress, disconnect and reconnect
  (**Retry**); confirm the button comes back as immediately tappable rather than waiting out the
  original 1.5s window (research.md's stale-timer fix).

```bash
# Optional: watch app-side horn logs
adb logcat | grep -i ambullrc
```

## Automated validation

### Unit tests (JVM — ViewModel logic, no hardware)

```bash
./gradlew :app:testDebugUnitTest
```

`ControlViewModelTest` gains horn coverage against `FakeEsp32Connection`, using
`kotlinx-coroutines-test` virtual time: `onHornTapped()` sends `"HORN\n"` only while `hornAvailable`
is true; advancing virtual time by the cooldown duration flips `hornPlaying`/`hornAvailable` back
without any fake ESP32 response; a disconnect mid-cooldown resets both immediately and a
reconnect is not left waiting on the stale timer; a held direction plus horn taps send both
without interruption (FR-007). See [contracts/horn-contract.md](./contracts/horn-contract.md).

### Instrumented UI test (device/emulator)

```bash
./gradlew :app:connectedDebugAndroidTest
```

`ControlScreenTest` is extended to assert the horn button's position, its three visual states
(Unavailable/Ready/Sounding), and that tapping it while disabled (unavailable or mid-cooldown) has
no effect.

## Definition of done (constitution Principle V)

- [ ] `:app:testDebugUnitTest` passes (`ControlViewModel` horn coverage + `FakeEsp32Connection`,
      including the cooldown-timing and disconnect-reset cases).
- [ ] `:app:connectedDebugAndroidTest` passes (horn button rendering/state).
- [ ] On-device smoke check against a real ESP32 that plays a sound on `HORN`: tap sounds the real
      horn, the button shows "sounding" for ~1.5s then re-enables on its own, repeat taps
      mid-cooldown are ignored, and directional commands are unaffected throughout.
