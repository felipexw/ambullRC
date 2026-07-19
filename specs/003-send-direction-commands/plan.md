# Implementation Plan: Send Direction Commands to ESP32

**Branch**: `003-send-direction-commands` | **Date**: 2026-07-18 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-send-direction-commands/spec.md`

## Summary

Every tap on a direction button (Up/Down/Left/Right) sends a distinct, plain-text, newline-
delimited command (`"UP\n"`, etc.) to the ESP32 over the Bluetooth link already established by
feature 002. `Esp32Connection` gains one non-throwing `send(message): Boolean` operation;
`ControlViewModel` becomes an AndroidX `ViewModel` (mirroring `ConnectionViewModel`) that calls it
on an injected I/O dispatcher after its existing tap-logging call. Taps made while disconnected are
silently dropped (`send` returns `false`, no exception, no queueing) per FR-005. `MainActivity`
shares one `BluetoothEsp32Connection` instance between `ConnectionViewModel` and `ControlViewModel`
so commands travel over the same live socket the connection feature manages.

## Technical Context

**Language/Version**: Kotlin 2.2.10 (JVM target 11) — unchanged from feature 002.

**Primary Dependencies**: No new dependencies. Reuses `androidx.lifecycle:lifecycle-viewmodel-ktx`
and `kotlinx-coroutines-android` (already added in feature 002) for `ControlViewModel`'s
`viewModelScope` + injected dispatcher. Bluetooth remains the platform `BluetoothSocket`
(`outputStream`, alongside the existing `inputStream`) — no third-party BT library.

**Storage**: N/A — no persistence. Commands are transient, fire-and-forget writes.

**Testing**: JUnit4 + `kotlinx-coroutines-test` (`app/src/test`, JVM unit tests of
`ControlViewModel` against `FakeEsp32Connection`, extended from feature 002's fake) and Compose UI
Test (`app/src/androidTest`, `ControlScreenTest` updated to construct `ControlViewModel` with a
fake connection). Both already used by the project; no new test libraries.

**Target Platform**: Android, minSdk 33 (Android 13) / targetSdk 36 — unchanged.

**Project Type**: Mobile app (single Android module `:app`) — unchanged.

**Performance Goals**: The tap-to-transmit handoff is not perceptible to the operator (SC-004) —
satisfied by dispatching the blocking socket write off the main thread via `viewModelScope` +
`Dispatchers.IO`, matching `ConnectionViewModel`'s existing pattern. No throughput target beyond
one message per tap.

**Constraints**: Stay minimal per the constitution — plain newline-delimited text, no new message
framework, no acknowledgment/response handling (one-way per Hardware & Communication Scope). `send`
never throws, so `ControlViewModel` needs no new error-handling branches. No real ESP32 needed to
run the automated suite (fake seam, extended from feature 002).

**Scale/Scope**: One new seam method (`send`), one `ControlViewModel` migration to AndroidX
`ViewModel`, one shared connection instance in `MainActivity`. No new files beyond test updates;
extends existing `Esp32Connection`/`BluetoothEsp32Connection`/`FakeEsp32Connection` from feature
002.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Assessment |
|-----------|------------|
| **I. Simplicity & YAGNI** | ✅ One new method on an existing seam; message format is the enum name plus a newline (no encoding table, no new dependency). `send` returns `Boolean` instead of introducing a new exception hierarchy — the simplest shape that satisfies FR-005/FR-006. No queueing, no acknowledgment handling. |
| **II. MVVM Architecture** | ✅ `ControlViewModel` (now an AndroidX `ViewModel`) is exactly where the constitution says command-sending logic belongs ("ViewModels ... contain the logic that decides which command to send"). The View (`ControlScreen`) is untouched in behavior — it still only forwards taps. `Esp32Connection`/`BluetoothEsp32Connection` remain the Model/hardware layer. |
| **III. Single Purpose** | ✅ This *is* the command-transmission purpose the whole app exists for. No telemetry read-back is added (`send` has no return payload from the ESP32, only a local success/failure boolean). |
| **IV. Function Over Form** | ✅ No UI changes at all — same four buttons, same visuals. All work is in the ViewModel/Model layers. |
| **V. Mandatory Test Coverage** | ✅ Unit tests drive `ControlViewModel` against `FakeEsp32Connection` for all four directions, repeated taps, and the disconnected no-op path (constitution's required ViewModel + Bluetooth-layer coverage via a fake, no hardware). Compose UI test (`ControlScreenTest`) continues to exercise taps end-to-end through the composable. The real `BluetoothEsp32Connection.send` is validated on-device via quickstart.md, consistent with how feature 002 validated its real adapter. |

**Result**: PASS. No new dependencies, no Complexity Tracking entries required.

## Project Structure

### Documentation (this feature)

```text
specs/003-send-direction-commands/
├── plan.md              # This file
├── spec.md              # Feature specification
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── command-contract.md   # Phase 1 output — extended seam + ControlViewModel contract
├── checklists/
│   └── requirements.md  # Spec quality checklist (from /speckit-specify)
└── tasks.md              # Phase 2 output (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
app/src/main/java/com/example/ambullrc/
├── MainActivity.kt                     # + shares one BluetoothEsp32Connection with both ViewModels
├── model/
│   ├── Direction.kt                    # existing, unchanged
│   ├── ConnectionState.kt              # existing, unchanged
│   └── Esp32Connection.kt              # + suspend fun send(message: String): Boolean
├── data/
│   ├── Esp32Config.kt                  # existing, unchanged
│   └── BluetoothEsp32Connection.kt     # + outputStream capture + send() implementation
├── viewmodel/
│   ├── ControlViewModel.kt             # becomes AndroidX ViewModel; calls connection.send() per tap
│   ├── DirectionLogger.kt              # existing, unchanged
│   └── ConnectionViewModel.kt          # existing, unchanged
└── ui/
    ├── ControlScreen.kt                # viewModel param now required (no default constructor)
    └── ConnectionStatusBar.kt          # existing, unchanged

app/src/test/java/com/example/ambullrc/
├── FakeEsp32Connection.kt              # + isConnected, sentCommands, sendShouldSucceed, send()
└── ControlViewModelTest.kt             # updated: constructs with FakeEsp32Connection; new FR-001..006 cases

app/src/androidTest/java/com/example/ambullrc/
└── ControlScreenTest.kt                # updated: constructs ControlViewModel with FakeEsp32Connection
```

**Structure Decision**: Single module `:app`, flat MVVM packages — unchanged from feature 002. This
feature only extends existing files (`Esp32Connection`, `BluetoothEsp32Connection`,
`FakeEsp32Connection`, `ControlViewModel`, `MainActivity`, and the two `ControlView*Test` files) and
adds no new production or test files, consistent with Principle I.

## Complexity Tracking

No constitution violations — no entries required.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| _(none)_  | _(n/a)_    | _(n/a)_                             |
