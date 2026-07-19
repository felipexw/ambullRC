# CLAUDE.md

Guidance for Claude Code (and other AI agents) working in this repository. See also
[`AGENTS.md`](AGENTS.md), which mirrors this content for tool-agnostic agents.

## What this project is

AmbullRC is an Android **Bluetooth remote control for an ESP32** that drives one servomotor (rear
steering) and one DC motor (engine). It is a personal **learning side-project**, not production. Its
single purpose is to send commands (signals) to the ESP32.

## Non-negotiable principles

These come from `.specify/memory/constitution.md` (v1.0.0). Read it before non-trivial changes.

1. **Simplicity & YAGNI (NON-NEGOTIABLE)** — build the simplest thing that works. No speculative
   abstractions, no features that aren't asked for.
2. **MVVM architecture** — keep the flat package split: `model`, `data`, `viewmodel`, `ui`.
3. **Single purpose** — the app only transmits commands to the ESP32. Don't add unrelated scope.
4. **Function over form** — UX/visual polish does not matter. Don't spend effort on styling.
5. **Mandatory test coverage** — every feature ships with **both** unit and integration tests.

## Architecture conventions

- **Testability seams**: hardware/framework-bound behavior sits behind an interface in `model/`
  (e.g. `Esp32Connection`, `DirectionLogger`) with a real implementation in `data/` and a **fake** in
  `app/src/test/`. This keeps ViewModels pure-JVM unit-testable — **preserve this pattern.**
- **ViewModels** use `viewModelScope`, `StateFlow`, and an **injected `CoroutineDispatcher`** so
  coroutine/timeout logic is testable with virtual time (`StandardTestDispatcher`, `runTest`).
- **Composables are stateless** — they take state + callbacks; the Activity wires them to ViewModels.
- **Bluetooth** is Classic RFCOMM/SPP (not BLE), single bonded device matched by name, using only the
  `BLUETOOTH_CONNECT` permission (no scan/location).

## Build & test

No system JDK exists — always export Android Studio's bundled JBR first:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

./gradlew testDebugUnitTest            # JVM unit tests (ViewModels vs. fakes) — run for logic changes
./gradlew connectedDebugAndroidTest    # Compose UI tests — needs emulator/device (emulator-5554, API 33)
./gradlew assembleDebug                # compile check
./gradlew installDebug                 # install on running emulator/device
```

- ADB: `$HOME/Library/Android/sdk/platform-tools/adb`
- The emulator has **no Bluetooth radio** — it can only validate the failure/retry/permission paths.
  Real auto-connect requires a **physical phone paired with an ESP32**.

## Working conventions

- **Spec Kit drives features.** Use the `/speckit-*` flow (specify → plan → tasks → analyze →
  implement) rather than ad-hoc coding when adding a feature. Artifacts live under `specs/<NNN-name>/`.
  Mark tasks `[X]` in `tasks.md` as you complete them.
- **Match surrounding code** — comment density, naming, Kotlin idioms.
- **Compose Material icons** (`KeyboardArrow*`) don't resolve transitively from the BOM; the
  `androidx.compose.material:material-icons-core` dependency is required (already added). Use the
  `Icons.AutoMirrored.Filled.*` variants for left/right to avoid deprecation warnings.
- **Don't commit tooling files**: `.clangd`, `.idea/`, `.vscode/`, `eim_config.toml` are local
  ESP-IDF / editor tooling and are intentionally left untracked.
- **Commit messages** end with the required `Co-Authored-By` trailer. Only commit/push when asked;
  branch off `main` first if needed.

## Current state (2026-07-19)

- Features 001 (direction buttons), 002 (ESP32 Bluetooth auto-connect), 003 (send direction
  commands), and 004 (in-app log viewer widget) are implemented and committed. Automated tests
  (unit + instrumented) pass for all four.
- Feature 003's on-device smoke check (**T011**) and feature 004's on-device validation
  (**T013**) are done — direction taps and connection-state changes were confirmed on a physical
  phone against the real ESP32, visible live in the in-app log widget.
- ⚠️ **Feature 002's own on-device task is still open**: **T020** in
  `specs/002-esp32-bluetooth-connection/tasks.md` (dedicated auto-connect smoke check — Retry
  recovery and the permission-denied path specifically). Remind Felipe to run it and mark it
  `[X]` when convenient.
- **2026-07-18 hold-to-drive change**: `ControlViewModel.onDirectionTapped` (single send per tap)
  was replaced with `onDirectionPressed`/`onDirectionReleased` — a press now resends that
  direction's command every 100ms until released, with no separate "stop" command; the ESP32 is
  expected to stop the motor itself once the stream goes quiet. See the "Post-ship change" note in
  `specs/003-send-direction-commands/tasks.md` for details. Automated tests (unit + instrumented)
  pass, but this has **not yet been validated on real hardware** — confirm with Felipe that the
  ESP32 firmware actually has (or gets) a watchdog that stops the motor when the direction stream
  stops arriving, and that release-while-held actually stops the motor on a physical run.
- Next planned work: none currently planned.
