# AGENTS.md

Tool-agnostic guidance for AI coding agents. Claude Code users: [`CLAUDE.md`](CLAUDE.md) has the same
content plus Claude-specific notes. Human contributors: see [`README.md`](README.md).

## Project

AmbullRC — an Android app that is a **Bluetooth remote control for an ESP32** driving one servomotor
(rear steering) and one DC motor (engine). Personal learning side-project, **not** production. Its
only job is to send commands to the ESP32.

## Rules to follow (from the constitution, `.specify/memory/constitution.md`)

1. **Simplicity & YAGNI (non-negotiable)** — simplest thing that works; no speculative abstractions.
2. **MVVM** — keep the flat packages `model` / `data` / `viewmodel` / `ui`.
3. **Single purpose** — command transmission to the ESP32 only; don't expand scope.
4. **Delightful UX, simple implementation** — polish UX/look-and-feel (feedback, motion, theming),
   but only via idiomatic Compose/Material 3 primitives; no bespoke frameworks or new layers.
5. **Mandatory tests** — every feature needs both unit and integration tests.

## Architecture

- Hardware/framework behavior lives behind an interface in `model/` (e.g. `Esp32Connection`,
  `DirectionLogger`), with a real impl in `data/` and a **fake** in `app/src/test/`. ViewModels stay
  pure-JVM unit-testable. **Keep this seam pattern.**
- ViewModels: `viewModelScope` + `StateFlow` + an **injected `CoroutineDispatcher`** (testable with
  `StandardTestDispatcher` / `runTest`).
- Composables are **stateless**; the Activity wires them to ViewModels.
- Bluetooth is **Classic RFCOMM/SPP** (UUID `00001101-0000-1000-8000-00805F9B34FB`), single bonded
  device by name, `BLUETOOTH_CONNECT` permission only.

## Environment & commands

No system JDK — export Android Studio's bundled JBR first:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

./gradlew testDebugUnitTest          # unit tests (run these for logic changes)
./gradlew connectedDebugAndroidTest  # instrumented Compose tests (needs emulator/device)
./gradlew assembleDebug              # compile check
./gradlew installDebug               # install on emulator/device
```

The emulator has no Bluetooth radio; real auto-connect must be verified on a physical phone paired
with an ESP32.

## Conventions

- **Add features via Spec Kit**: `/speckit-specify` → `/speckit-plan` → `/speckit-tasks` →
  `/speckit-analyze` → `/speckit-implement`. Artifacts under `specs/<NNN-name>/`; mark tasks `[X]` as
  done.
- Match surrounding code style (naming, comments, Kotlin idioms).
- `androidx.compose.material:material-icons-core` is required for `KeyboardArrow*` icons; use
  `Icons.AutoMirrored.Filled.*` for left/right.
- Do **not** commit local tooling: `.clangd`, `.idea/`, `.vscode/`, `eim_config.toml`.
- Commit/push only when asked; branch off `main` first.

## Current state

Features 001 (direction buttons), 002 (ESP32 Bluetooth auto-connect), 003 (send direction
commands), 004 (in-app log viewer widget), and 005 (home screen UX redesign) are implemented,
with passing unit + instrumented tests. On-device validation is done for direction commands and
the log widget (task **T011** in `specs/003-send-direction-commands/tasks.md` and task **T013**
in `specs/004-in-app-log-viewer/tasks.md`). Task **T020** in
`specs/002-esp32-bluetooth-connection/tasks.md` (dedicated auto-connect smoke check) remains
open.

**Feature 005** redesigned the header (color-coded/animated connection status pill), the D-pad
(cross layout, pressed/disabled visual states), and the log panel (tap-or-drag collapsible sheet,
timestamped/category-colored entries), per `specs/005-home-screen-ux-redesign/`. It also fixed the
theme to a single dark palette (no more Material You dynamic color) and gave log entries structure
(`viewmodel/LogEntry.kt`) instead of plain strings. Verified visually on the emulator; task **T023**
in `specs/005-home-screen-ux-redesign/tasks.md` (on-device ESP32 validation) remains open.

**2026-07-18 hold-to-drive change**: `ControlViewModel.onDirectionTapped` (single send per tap)
was replaced with `onDirectionPressed`/`onDirectionReleased` — a press resends that direction's
command every 100ms until released, with no separate "stop" command; the ESP32 is expected to
stop the motor itself once the stream goes quiet. See the "Post-ship change" note in
`specs/003-send-direction-commands/tasks.md`. Unit + instrumented tests pass, but this has **not
yet been validated on real hardware** — confirm the ESP32 firmware actually stops the motor when
the direction stream stops arriving.

No further work is currently planned.
