# Implementation Plan: In-App Log Viewer Widget

**Branch**: `004-in-app-log-viewer` | **Date**: 2026-07-18 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-in-app-log-viewer/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Add a small, read-only, auto-updating log panel directly below the direction control arrows on the
home screen, so the operator can see connection-status changes and direction-command outcomes
on-device without a computer. Implemented as a bounded in-memory ring buffer that `ConnectionViewModel`
and `ControlViewModel` write human-readable entries to at the moments already required by FR-002
(state transitions, tap send/drop outcomes); a stateless Composable renders it. No new dependencies,
no persistence, no real Android Logcat/stdout capture (see research.md for why).

## Technical Context

**Language/Version**: Kotlin 2.2.10

**Primary Dependencies**: Jetpack Compose (Material3), AndroidX Lifecycle/ViewModel, kotlinx-coroutines
(StateFlow) — all already in use by features 001–003; no new dependencies.

**Storage**: N/A — in-memory only, scoped to the current app process/session (FR-008).

**Testing**: JUnit4 + kotlinx-coroutines-test (`StandardTestDispatcher`, `runTest`) for the ring buffer
and ViewModel wiring (`app/src/test`); Jetpack Compose UI testing (`createComposeRule`, `app/src/androidTest`)
for the on-screen widget — same tooling already used for features 001–003.

**Target Platform**: Android, minSdk 33 / targetSdk 36 (existing app configuration, unchanged).

**Project Type**: Mobile app — single Android module, existing MVVM package split
(`model` / `data` / `viewmodel` / `ui`).

**Performance Goals**: New entries reflected on screen within 1s of the event occurring (SC-002);
correct behavior through at least 50 consecutive events without slowdown (SC-003) — trivially met at
human tap-rate frequency with a bounded in-memory list; not a real performance-engineering concern.

**Constraints**: Bounded history size so memory does not grow unbounded over a long session (FR-007);
no new runtime permissions (rules out shelling out to the `logcat` binary, which is also
permission-fragile across OEMs/Android versions — see research.md).

**Scale/Scope**: One shared log buffer instance, written to by the two existing ViewModels
(`ConnectionViewModel`, `ControlViewModel`) and rendered by one new Composable. Single local device,
single operator — no multi-user/networked concerns.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Status |
|---|---|---|
| I. Simplicity & YAGNI | Plain in-memory ring buffer (bounded list + `StateFlow`), no new dependency, no persistence, no real Logcat capture. No configurability beyond what FR-001..008 require. | PASS |
| II. MVVM Architecture | Log buffer is written to by ViewModels only; the Composable is stateless (renders entries passed in, no logic). Views still never talk to the Bluetooth layer directly — unchanged. | PASS |
| III. Single Purpose | Widget surfaces diagnostics about the app's own command-transmission behavior (connection state, command sent/dropped) — it is a debugging aid for the existing single purpose, not a new capability (no telemetry-from-ESP32, no analytics, no cloud/export). | PASS |
| IV. Function Over Form | Plain scrollable text list, no styling/animation effort. | PASS |
| V. Mandatory Test Coverage | Unit tests for the ring buffer (ordering, cap) and for ViewModel-to-buffer wiring; Compose UI test for the widget rendering below the control arrows and updating live — both required before merge. | PASS (planned in Phase 1/tasks) |

No violations — Complexity Tracking table not needed.

## Project Structure

### Documentation (this feature)

```text
specs/004-in-app-log-viewer/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
app/src/main/java/com/example/ambullrc/
├── viewmodel/
│   ├── DebugLog.kt              # NEW — bounded ring buffer of log-entry strings, exposed as StateFlow
│   ├── ConnectionViewModel.kt   # MODIFIED — writes a DebugLog entry on each state transition
│   └── ControlViewModel.kt      # MODIFIED — writes a DebugLog entry on each tap's send outcome
├── ui/
│   ├── DebugLogPanel.kt         # NEW — stateless Composable rendering the entry list
│   └── ControlScreen.kt         # unchanged (panel is placed by MainActivity, not inside this screen)
└── MainActivity.kt              # MODIFIED — instantiates one shared DebugLog, passes into both
                                  #   ViewModel factories, renders DebugLogPanel below ControlScreen

app/src/test/java/com/example/ambullrc/
├── DebugLogTest.kt              # NEW — ring buffer ordering/cap unit tests
├── ConnectionViewModelTest.kt   # NEW — asserts expected entries land in DebugLog per state change
└── ControlViewModelTest.kt      # MODIFIED — asserts expected entries land in DebugLog per tap

app/src/androidTest/java/com/example/ambullrc/
└── MainActivityLogWidgetTest.kt # NEW — Compose UI test: widget present below arrows, updates live
```

**Structure Decision**: Extends the existing single-module Android app in place — no new source
sets, no new modules. Follows the established `model`/`data`/`viewmodel`/`ui` split: `DebugLog` lives
in `viewmodel` (pure Kotlin, no Android framework dependency, directly unit-testable — same reasoning
as why `ConnectionState`/`Direction` live in `model` rather than needing a data/fake seam) and
`DebugLogPanel` lives in `ui` as a stateless Composable, mirroring `ConnectionStatusBar`.

## Post-Design Constitution Re-check

Phase 0/1 design (research.md, data-model.md, contracts/, quickstart.md) introduced no new dependency,
no persistence, no Android-framework-bound seam, and no capability beyond FR-001..008. The Constitution
Check table above still holds unchanged after design — no new violations surfaced.

## Complexity Tracking

Not applicable — no Constitution Check violations.
