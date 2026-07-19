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
4. **Function over form** — UX/styling does not matter; don't invest effort there.
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
commands), and 004 (in-app log viewer widget) are implemented, with passing unit + instrumented
tests. On-device validation is done for direction commands and the log widget (task **T011** in
`specs/003-send-direction-commands/tasks.md` and task **T013** in
`specs/004-in-app-log-viewer/tasks.md`). Task **T020** in
`specs/002-esp32-bluetooth-connection/tasks.md` (dedicated auto-connect smoke check) remains
open. No further work is currently planned.
