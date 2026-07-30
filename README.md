# AmbullRC

An Android app that acts as a **Bluetooth remote control for an ESP32 microcontroller**. The ESP32
drives a small RC vehicle: one **servomotor** (rear steering) and one **DC motor** (engine). The app
sends commands (signals) to the ESP32 — that is its single purpose. The ESP32 controller code is [here](https://github.com/felipexw/ambullRC-ESP32)

> This is a personal learning side-project for exploring electronics, **not** a production app. UX is
> deliberately minimal. Simplicity (YAGNI) is the governing principle.

[demo (WIP)](https://www.youtube.com/watch?v=EBZQrzWVDxw&list=PLal27zLmiqjQ&index=1)

## What it does

- **On startup**, auto-connects to a bonded ESP32 over **Bluetooth Classic (RFCOMM / SPP)**.
- Shows a minimal **connection status bar** (Not connected / Connecting… / Connected / failure reason)
  with a **Retry** button when a connection fails.
- Presents a **four-arrow D-pad** (up / down / left / right). Each button currently logs its tap
  (the actual command transmission to the ESP32 is the next feature — the seam is already in place).

## Tech stack

| Area | Choice |
|------|--------|
| Language | Kotlin 2.2.10 |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM (`model` / `data` / `viewmodel` / `ui`) |
| Async | Kotlin Coroutines + AndroidX ViewModel (`StateFlow`) |
| Bluetooth | Classic RFCOMM/SPP, UUID `00001101-0000-1000-8000-00805F9B34FB` |
| Min / Target / Compile SDK | 33 (Android 13) / 36 / 36 |
| Package | `com.example.ambullrc` |
| Modules | single `:app` |

## Project structure

```
app/src/main/java/com/example/ambullrc/
├── MainActivity.kt            # Hosts status bar + control screen; wires permission → connect
├── model/                     # Pure data + interface seams (no Android framework)
│   ├── Direction.kt           # UP / DOWN / LEFT / RIGHT
│   ├── ConnectionState.kt     # Idle / Connecting / Connected / Failed(reason)
│   └── Esp32Connection.kt     # Connection interface + exception types (the testability seam)
├── data/                      # Android/hardware-bound implementations
│   ├── Esp32Config.kt         # Device name + SPP UUID
│   └── BluetoothEsp32Connection.kt  # Real RFCOMM adapter
├── viewmodel/                 # Framework-light, JVM-unit-testable
│   ├── ControlViewModel.kt / DirectionLogger.kt
│   └── ConnectionViewModel.kt # Connect/timeout/retry/drop state machine
└── ui/                        # Stateless Composables
    ├── ControlScreen.kt / ConnectionStatusBar.kt

specs/                         # Spec Kit artifacts (spec / plan / tasks per feature)
.specify/memory/constitution.md  # Project principles (non-negotiable)
```

## Building & testing

There is no system JDK; use Android Studio's bundled JBR (JDK 21):

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

./gradlew assembleDebug        # build
./gradlew testDebugUnitTest    # JVM unit tests (ViewModels, against fakes)
./gradlew connectedDebugAndroidTest   # Compose UI tests (needs an emulator/device)
./gradlew installDebug         # install on the running emulator/device
```

An emulator (`Medium_Phone`, API 33) covers the failure/retry/permission paths. The **successful
Bluetooth auto-connect** path can only be verified on a **physical phone paired with a real ESP32**.

## Development workflow

Features are driven through **[Spec Kit](https://github.com/github/spec-kit)** slash commands:
`/speckit-specify` → `/speckit-plan` → `/speckit-tasks` → `/speckit-analyze` → `/speckit-implement`.
Every feature ships with unit **and** integration tests (see the constitution, Principle V).

## Status

| Feature | State |
|---------|-------|
| 001 — Direction buttons | Implemented + tested |
| 002 — ESP32 Bluetooth auto-connect | Implemented; ⚠️ **not yet verified on real hardware** (task T020 pending until ESP32 firmware is deployed) |
| Command transmission to ESP32 | Not started (the `Esp32Connection` seam is the attach point) |
