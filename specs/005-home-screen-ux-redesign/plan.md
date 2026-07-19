# Implementation Plan: Home Screen UX Redesign

**Branch**: `005-home-screen-ux-redesign` | **Date**: 2026-07-19 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/005-home-screen-ux-redesign/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Redesign the home screen's three existing areas — connection header, D-pad control area, and
diagnostic log panel — to match the high-fidelity design handoff, without changing any underlying
behavior (real connection lifecycle, direction press/release command sending, log capture). The
header gets a color-coded, animated status pill; the D-pad gets a cross layout with clear
pressed/disabled visual states; the log panel becomes a tap-or-drag collapsible sheet with
color-coded, timestamped entries. Implemented entirely with native Compose/Material3 primitives
(no new dependency) plus one small data-model upgrade — log entries gain a timestamp, category,
and level so the panel can color-code them, replacing today's plain-string entries.

## Technical Context

**Language/Version**: Kotlin 2.2.10

**Primary Dependencies**: Jetpack Compose (Material3), AndroidX Lifecycle/ViewModel,
kotlinx-coroutines (StateFlow) — all already in use by features 001–004; no new dependency.
Constitution Principle IV requires this redesign to stay within idiomatic Compose/Material3
theming, `animateXAsState`/`AnimatedVisibility`, and Compose foundation gesture APIs — no
third-party animation or design-system library.

**Storage**: N/A — log entries remain in-memory only, scoped to the current app process/session
(unchanged from feature 004).

**Testing**: JUnit4 + kotlinx-coroutines-test (`StandardTestDispatcher`, `runTest`) for the
log-entry/ViewModel changes (`app/src/test`); Jetpack Compose UI testing (`createComposeRule`,
`app/src/androidTest`) for the three redesigned Composables — same tooling already used for
features 001–004.

**Target Platform**: Android, minSdk 33 / targetSdk 36 (existing app configuration, unchanged).

**Project Type**: Mobile app — single Android module, existing MVVM package split
(`model` / `data` / `viewmodel` / `ui`).

**Performance Goals**: Direction-control pressed-state visual must appear on the same frame as
touch-down and clear on the same frame as release (SC-002) — met by Compose's built-in
interaction-state-driven animation, already the pattern in use. Log panel open/close transitions
animate over a short (~200ms), fixed duration so they read as smooth rather than instant or
sluggish (FR-010) — not a hard measured performance target, just bounded animation duration.

**Constraints**: No new dependencies (Constitution Principle IV). Fixed dark palette rather than
following the system light/dark setting or Material You dynamic color — the design is a single
dark, high-contrast screen with no light variant requested, and building one would be speculative
scope beyond what was asked (Principle I). Existing Compose test tags
(`btn_up`/`btn_down`/`btn_left`/`btn_right`, `btn_retry`, `status_text`, `debug_log`) are a
starting point, not a hard constraint — this feature's instrumented tests are expected to change
alongside the visuals they assert on.

**Scale/Scope**: Exactly the three areas named in the spec (header, control area, log panel) plus
the one shared data-shape change (log entries) they depend on. Still a single screen, single
Android module, no navigation, no settings.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Status |
|---|---|---|
| I. Simplicity & YAGNI | Fixed single accent/palette (not the design's swappable accent list); no settings UI. The one piece of custom logic — a drag-to-collapse sheet — is built directly on Compose foundation gesture APIs because no stock Material3 component offers a non-modal, tap-or-drag, partial-height sheet; this is required by FR-009/FR-010, not speculative. | PASS |
| II. MVVM Architecture | All three redesigned Composables stay stateless (state + callbacks in, no direct Bluetooth access). Ephemeral, no-business-meaning UI state (log panel open/closed, drag offset) stays local Compose state, mirroring the existing `DirectionButton` interaction-source precedent, rather than being promoted into a ViewModel. | PASS |
| III. Single Purpose | Presentation-only redesign of the existing single screen; no new capability, no telemetry, no navigation, no scope growth. | PASS |
| IV. Delightful UX, Simple Implementation | This feature *is* Principle IV: color-coded connection feedback, responsive press states, smooth sheet motion — delivered entirely through `MaterialTheme`, `animateColorAsState`/`animateDpAsState`/`AnimatedVisibility`, and Compose foundation gesture detection. No bespoke animation framework, no third-party design system, no new architectural layer. | PASS |
| V. Mandatory Test Coverage | Unit tests cover the log entry's new category/level-aware `add()` and the ViewModels' updated call sites; Compose UI tests cover pressed/disabled control states, the status pill per connection state, and the log panel's tap/drag open-close behavior. All required before merge. | PASS (planned in Phase 1/tasks) |

No violations — Complexity Tracking table not needed.

## Project Structure

### Documentation (this feature)

```text
specs/005-home-screen-ux-redesign/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md         # Phase 1 output (/speckit-plan command)
├── contracts/
│   └── ui-contract.md   # Phase 1 output (/speckit-plan command)
└── tasks.md              # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
app/src/main/java/com/example/ambullrc/
├── ui/theme/
│   ├── Color.kt                 # MODIFIED — design-token palette (background/surface/accent/
│   │                             #   status colors) replacing the default Compose starter colors
│   └── Theme.kt                 # MODIFIED — fixed dark scheme; dynamic Material You color disabled
├── ui/
│   ├── ConnectionStatusBar.kt   # MODIFIED — color-coded/animated status pill + device name,
│   │                             #   same (state, onRetry) params
│   ├── ControlScreen.kt         # MODIFIED — cross D-pad layout, pressed/disabled visuals, hint
│   │                             #   text; same ControlViewModel API
│   └── DebugLogPanel.kt         # MODIFIED — collapsible/draggable sheet, color-coded entries;
│                                 #   takes List<LogEntry> instead of List<String>
├── viewmodel/
│   ├── LogEntry.kt              # NEW — LogEntry data class + LogCategory/LogLevel enums
│   ├── DebugLog.kt              # MODIFIED — entries: StateFlow<List<LogEntry>>;
│   │                             #   add(category, level, message) replaces add(message)
│   ├── ConnectionViewModel.kt   # MODIFIED — call sites pass a category/level
│   └── ControlViewModel.kt      # MODIFIED — call sites pass a category/level
└── MainActivity.kt              # MODIFIED only if the log panel's overlay placement requires a
                                  #   Box instead of a Column (see research.md Decision 4)

app/src/test/java/com/example/ambullrc/
├── DebugLogTest.kt              # MODIFIED — asserts category/level-aware entries and the cap
├── ConnectionViewModelTest.kt   # MODIFIED — asserts new call-site categories/levels
└── ControlViewModelTest.kt      # MODIFIED — asserts new call-site categories/levels

app/src/androidTest/java/com/example/ambullrc/
├── ConnectionStatusBarTest.kt   # MODIFIED — asserts pill color/animation/label per state
├── ControlScreenTest.kt         # MODIFIED — asserts pressed/disabled visuals and hint text
└── DebugLogPanelTest.kt         # MODIFIED — asserts collapse/expand via tap & drag, entry coloring
```

**Structure Decision**: Extends the existing single-module Android app in place — no new source
sets, no new modules, no new top-level packages. Follows the established `model` / `data` /
`viewmodel` / `ui` split: the new `LogEntry` type lives in `viewmodel` alongside `DebugLog`, for
the same reason `DebugLog` itself does (pure Kotlin, no Android/hardware dependency, directly
unit-testable — no fake/seam needed). All three redesigned screens stay in `ui` as stateless
Composables.

## Post-Design Constitution Re-check

Phase 0/1 design (research.md, data-model.md, contracts/, quickstart.md) introduced no new
dependency, no persistence, no Android-framework-bound seam beyond what already exists, and no
capability beyond FR-001..015. The custom drag-collapsible sheet and the fixed-palette theme are
both built from Compose/Material3 primitives already in the project. The Constitution Check table
above still holds unchanged after design — no new violations surfaced.

## Complexity Tracking

Not applicable — no Constitution Check violations.
