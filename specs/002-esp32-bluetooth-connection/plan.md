# Implementation Plan: ESP32 Bluetooth Connection on Startup

**Branch**: `002-esp32-bluetooth-connection` | **Date**: 2026-07-17 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-esp32-bluetooth-connection/spec.md`

## Summary

When the app starts, it automatically establishes a Bluetooth Classic (RFCOMM / SPP) link to a
single, already-paired ESP32, exposes the connection state (idle → connecting → connected, or
failed with a reason), and lets the operator retry after a failure. Following the MVVM
constitution, a `ConnectionViewModel` owns a `StateFlow<ConnectionState>` and orchestrates the
connect / monitor / retry logic against an `Esp32Connection` seam interface. The real
implementation wraps `BluetoothAdapter`/`BluetoothSocket`; a fake implementation stands in for the
Android Bluetooth API so the whole state machine is testable with no ESP32 hardware. Actually
sending the direction commands over this link is deferred to a follow-up feature — this feature
delivers the connection and its observable status only.

## Technical Context

**Language/Version**: Kotlin 2.2.10 (JVM target 11)

**Primary Dependencies**: Jetpack Compose (BOM 2026.02.01) + Material3 (existing). **New (justified
below)**: `kotlinx-coroutines-android` (async blocking Bluetooth I/O + `viewModelScope`),
`androidx.lifecycle:lifecycle-viewmodel-ktx` (AndroidX `ViewModel` + `viewModelScope`). Test-only:
`kotlinx-coroutines-test`. Bluetooth uses the Android platform `BluetoothAdapter`/`BluetoothSocket`
(no third-party BT library).

**Storage**: N/A — no persistence. The target device identity is a compile-time constant.

**Testing**: JUnit4 + `kotlinx-coroutines-test` (`app/src/test`, JVM unit tests of the
`ConnectionViewModel` state machine against a fake `Esp32Connection`) and Compose UI Test
(`app/src/androidTest`, instrumented rendering of the status UI per connection state). Both test
libraries the project already has, plus coroutines-test.

**Target Platform**: Android, minSdk 33 (Android 13) / targetSdk 36.

**Project Type**: Mobile app (single Android module `:app`).

**Performance Goals**: Reach connected within 10 s when the ESP32 is available (SC-001); report
failure within 15 s otherwise (SC-003) via a connect timeout. No throughput target (no command
traffic in this feature).

**Constraints**: Stay minimal per the constitution (Classic SPP over BLE; single bonded device by
name; connection-only). Runtime permission `BLUETOOTH_CONNECT` required on Android 13. No real
ESP32 needed to run the automated suite (fake seam).

**Scale/Scope**: One connection, one target device, one ViewModel, one status composable, one
seam interface + one real adapter.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Assessment |
|-----------|------------|
| **I. Simplicity & YAGNI** | ✅ Bluetooth **Classic SPP** (simplest transport; matches the "already paired/bonded" assumption), a single target device identified by name, connection-only (no command send, no telemetry). No DI framework, no repository abstraction beyond the one seam needed for testability. New dependencies (coroutines, lifecycle-viewmodel) are added only because async blocking Bluetooth I/O genuinely requires them — see research.md. |
| **II. MVVM Architecture** | ✅ `ConnectionViewModel` (AndroidX `ViewModel`) exposes `StateFlow<ConnectionState>` and holds all connect/retry/monitor logic. `Esp32Connection` + its real `BluetoothEsp32Connection` are the Model/hardware layer. The View (`ConnectionStatusBar` composable) only renders state and forwards a retry action; it never touches the Bluetooth API. |
| **III. Single Purpose** | ✅ Establishes the command channel to the ESP32. The liveness monitor that detects a dropped link discards any bytes (no telemetry is read back), staying within the one-way-command scope. |
| **IV. Function Over Form** | ✅ Minimal status text ("Connecting…", "Connected", "Not connected — <reason>") plus a Retry button shown only on failure. No polished connection UI. |
| **V. Mandatory Test Coverage** | ✅ Unit tests drive the full `ConnectionViewModel` state machine (connect success, each failure reason, retry, mid-session drop, no-false-connected) against a **fake `Esp32Connection`** — the constitution's required integration-level coverage of the Bluetooth layer without hardware. Compose UI tests assert the status UI renders each state and the Retry action fires. The thin real `BluetoothEsp32Connection` adapter is validated on-device via quickstart.md. |

**Result**: PASS. Dependency additions are justified (not violations); no Complexity Tracking
entries required.

## Project Structure

### Documentation (this feature)

```text
specs/002-esp32-bluetooth-connection/
├── plan.md              # This file
├── spec.md              # Feature specification
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── connection-contract.md   # Phase 1 output — seam + ViewModel + UI contract
├── checklists/
│   └── requirements.md  # Spec quality checklist (from /speckit-specify)
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
app/src/main/java/com/example/ambullrc/
├── MainActivity.kt                     # Requests BLUETOOTH_CONNECT; hosts status bar + ControlScreen
├── model/
│   ├── Direction.kt                    # existing
│   ├── ConnectionState.kt              # sealed ConnectionState (Idle/Connecting/Connected/Failed) + FailureReason
│   └── Esp32Connection.kt              # seam interface + typed connection exceptions
├── data/
│   ├── Esp32Config.kt                  # target device name + SPP UUID constants
│   └── BluetoothEsp32Connection.kt     # real Esp32Connection over BluetoothAdapter/BluetoothSocket
├── viewmodel/
│   ├── ControlViewModel.kt             # existing
│   ├── DirectionLogger.kt              # existing
│   └── ConnectionViewModel.kt          # AndroidX ViewModel; StateFlow<ConnectionState>; connect()/retry()/onPermissionDenied()
└── ui/
    ├── ControlScreen.kt                # existing
    └── ConnectionStatusBar.kt          # stateless composable(state, onRetry)

app/src/main/AndroidManifest.xml        # + <uses-permission BLUETOOTH_CONNECT>

app/src/test/java/com/example/ambullrc/
└── ConnectionViewModelTest.kt          # JVM unit tests with FakeEsp32Connection + coroutines-test

app/src/androidTest/java/com/example/ambullrc/
└── ConnectionStatusBarTest.kt          # Compose UI test: each state renders; Retry fires
```

**Structure Decision**: Single module `:app`, flat MVVM packages. The Model layer is split into the
abstraction (`model/Esp32Connection.kt`, `model/ConnectionState.kt`) and the concrete platform
implementation (`data/BluetoothEsp32Connection.kt`, `data/Esp32Config.kt`), which keeps the ViewModel
and its tests free of Android Bluetooth classes. Existing 001 files are untouched; `MainActivity`
gains permission handling and hosts the status bar above the existing `ControlScreen`.

## Complexity Tracking

> No constitution violations. The new dependencies are permitted under the constitution's "adopt a
> new dependency only when the task genuinely cannot be done reasonably without it" clause and are
> justified in research.md, so no violation entries are required.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| _(none)_  | _(n/a)_    | _(n/a)_                             |
