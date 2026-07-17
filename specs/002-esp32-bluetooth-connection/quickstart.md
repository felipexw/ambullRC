# Quickstart & Validation: ESP32 Bluetooth Connection on Startup

How to build, configure, run, and validate the connection feature. Implementation details live in
tasks.md / the source; this is a run-and-verify guide.

## Prerequisites

- JDK 11+ and the Android SDK (compileSdk 36, minSdk 33). Use the bundled `./gradlew`.
- For automated tests: an emulator or device on API 33+ (no ESP32 required — tests use a fake).
- For the on-device smoke check: a real Android phone (API 33+), and an **ESP32 already paired
  (bonded)** with that phone, running a Bluetooth Classic **SPP / SerialBT** sketch that advertises
  the name you configure below. (An emulator has no real Bluetooth radio, so the live smoke check
  needs a physical phone.)

## Configure the target device

Set the ESP32's advertised Bluetooth name in `data/Esp32Config.kt`:

```kotlin
const val DEVICE_NAME = "AmbullRC-ESP32"   // ← change to your ESP32's Bluetooth name
```

The app matches this against the phone's **bonded** devices; pair the ESP32 in Android Settings
first. The SPP UUID (`00001101-…`) is standard and normally needs no change.

## Build

```bash
./gradlew :app:assembleDebug
```

Expected: build succeeds with the new `BLUETOOTH_CONNECT` permission and coroutines/lifecycle
dependencies.

## Run the app (on a physical phone with the ESP32)

```bash
./gradlew :app:installDebug
```

Validate against the spec:

- **US1 / SC-001**: With the ESP32 powered on and in range, launch the app. Grant the Bluetooth
  permission when prompted. The status bar shows "Connecting…" then "Connected" within ~10 s, with
  no manual connect step.
- **US2 / SC-002**: The status bar always shows the current status (Connecting / Connected / Not
  connected — reason).
- **US3 / SC-003 / SC-004**: Power the ESP32 off and relaunch (or toggle it off while running). The
  status shows "Not connected — …" within ~15 s and a **Retry** button appears. Power the ESP32 back
  on, tap **Retry**, and confirm it reaches "Connected".
- **Permissions (FR-003)**: Deny the Bluetooth permission → status shows the permission-denied
  failure with Retry.

```bash
# Optional: watch connection logs
adb logcat | grep -i ambullrc
```

## Automated validation

### Unit tests (JVM — ViewModel state machine, no hardware)

```bash
./gradlew :app:testDebugUnitTest
```

`ConnectionViewModelTest` drives `ConnectionViewModel` against `FakeEsp32Connection` with
`kotlinx-coroutines-test`, covering: connect success (FR-001/005), each failure reason
(FR-003/006), connect timeout (SC-003), retry (FR-007/SC-004), mid-session drop (FR-008), and the
no-false-`Connected` invariant (SC-005). See
[contracts/connection-contract.md](./contracts/connection-contract.md).

### Instrumented UI test (device/emulator — status rendering)

```bash
./gradlew :app:connectedDebugAndroidTest
```

`ConnectionStatusBarTest` asserts each `ConnectionState` renders the right status text (via
`status_text`), that the **Retry** button appears only for `Failed` and invokes `onRetry`
(`btn_retry`), and is absent otherwise (US2, FR-004, FR-007).

## Definition of done (constitution Principle V)

- [ ] `:app:testDebugUnitTest` passes (ViewModel + fake connection).
- [ ] `:app:connectedDebugAndroidTest` passes (status UI).
- [ ] On-device smoke check: auto-connect, failure+retry, and permission-denied paths behave per
      spec with a real bonded ESP32.
