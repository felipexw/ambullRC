# Quickstart & Validation: Send Direction Commands to ESP32

How to build, run, and validate that direction-button taps reach the ESP32. Implementation details
live in tasks.md / the source; this is a run-and-verify guide. Builds on
[002's quickstart](../002-esp32-bluetooth-connection/quickstart.md) — the app must already
auto-connect before commands can be sent.

## Prerequisites

- Everything in [002's quickstart prerequisites](../002-esp32-bluetooth-connection/quickstart.md#prerequisites).
- For the on-device smoke check: the ESP32 sketch should print (or otherwise surface) each line it
  receives over the SPP serial connection, so you can confirm `UP` / `DOWN` / `LEFT` / `RIGHT`
  arrive as expected — e.g. `Serial.println(Serial.readStringUntil('\n'))` echoed to the ESP32's
  own USB serial monitor, or an LED/behavior change per direction.

## Build

```bash
./gradlew :app:assembleDebug
```

Expected: build succeeds with no new dependencies (reuses the coroutines/lifecycle-viewmodel
dependencies added in feature 002).

## Run the app (on a physical phone with the ESP32)

```bash
./gradlew :app:installDebug
```

Validate against the spec:

- **US1 / SC-001, SC-002**: Let the app auto-connect (feature 002). Tap each of the four direction
  buttons in turn while watching the ESP32's serial monitor. Confirm one line arrives per tap, and
  that each direction produces a distinct, correctly-matching line (`UP`, `DOWN`, `LEFT`, `RIGHT`).
- **US1 / SC-004**: Tap rapidly several times in a row; confirm every tap produces its own line
  with no missed or merged commands, and no perceptible lag between tap and arrival.
- **US2 / SC-003**: Power off the ESP32 (or leave the app in a "Not connected" state per the status
  bar) and tap direction buttons. Confirm the app does not crash and remains responsive to further
  taps. Then power the ESP32 back on, use **Retry** (feature 002) to reconnect, and confirm
  subsequently tapped directions arrive normally — the taps made while disconnected are not
  replayed.

```bash
# Optional: watch app-side tap logs
adb logcat | grep -i ambullrc
```

## Automated validation

### Unit tests (JVM — ViewModel logic, no hardware)

```bash
./gradlew :app:testDebugUnitTest
```

`ControlViewModelTest` drives `ControlViewModel` against `FakeEsp32Connection` with
`kotlinx-coroutines-test`, covering: one matching message per direction (FR-001/FR-002), exactly
one message per tap including repeated taps (FR-003), the message carrying only direction identity
(FR-004), and taps while disconnected producing no recorded message and no crash (FR-005/FR-006).
See [contracts/command-contract.md](./contracts/command-contract.md).

### Instrumented UI test (device/emulator)

```bash
./gradlew :app:connectedDebugAndroidTest
```

`ControlScreenTest` is updated to construct `ControlViewModel` with a `FakeEsp32Connection` and
continues to assert each button tap drives the ViewModel correctly end-to-end from the composable.

## Definition of done (constitution Principle V)

- [ ] `:app:testDebugUnitTest` passes (`ControlViewModel` + `FakeEsp32Connection`).
- [ ] `:app:connectedDebugAndroidTest` passes (control screen taps).
- [ ] On-device smoke check: each direction tap produces a distinct, correctly-identified message
      on a real bonded ESP32, and tapping while disconnected does not crash the app.
