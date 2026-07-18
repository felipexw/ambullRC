# Implementation Plan: Direction Buttons Screen

**Branch**: `001-direction-buttons` | **Date**: 2026-07-17 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-direction-buttons/spec.md`

## Summary

Deliver a single control screen presenting four directional buttons — up, down, left,
right — each showing the matching arrow icon, and each producing a distinct log record
when tapped. Following the project's MVVM constitution, the screen is a stateless
Jetpack Compose Composable (View) that forwards taps to a `ControlViewModel`, which
records the tapped `Direction` through an injectable logging seam. That seam keeps the
ViewModel unit-testable on the JVM and is the exact place the future Bluetooth
command-transmission feature will attach — no Bluetooth code is written now.

## Technical Context

**Language/Version**: Kotlin 2.2.10 (JVM target 11)

**Primary Dependencies**: Jetpack Compose (BOM 2026.02.01), Compose Material3,
Activity Compose, AndroidX Lifecycle — all already declared in `gradle/libs.versions.toml`.
No new runtime dependencies required (see research.md for the Material icons note).

**Storage**: N/A — no persistence in this feature.

**Testing**: JUnit4 (`app/src/test`, JVM unit tests for the ViewModel) and Compose UI
Test / `androidx.compose.ui.test.junit4` with Espresso (`app/src/androidTest`,
instrumented UI test for the screen). Both are already configured in `app/build.gradle.kts`.

**Target Platform**: Android, minSdk 33 / targetSdk 36.

**Project Type**: Mobile app (single Android module `:app`).

**Performance Goals**: None specific. Tap-to-log must feel immediate (standard Compose
recomposition, no perceptible delay); no throughput or frame-rate targets apply.

**Constraints**: Must stay within existing dependencies and standard Android tooling
(constitution: favor defaults, no new frameworks/DI). UI polish is explicitly out of
scope. Every tap must be recorded individually with no cross-firing.

**Scale/Scope**: One screen, four controls, one ViewModel, one enum. No navigation,
no auth, single operator.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Assessment |
|-----------|------------|
| **I. Simplicity & YAGNI** | ✅ One Composable, one ViewModel, one `Direction` enum, one one-method logger seam. No repository, no DI framework, no Bluetooth abstraction, no navigation. Nothing built for hypothetical futures. |
| **II. MVVM Architecture** | ✅ View (`ControlScreen` Composable) holds no logic — it only renders four buttons and forwards taps. `ControlViewModel` owns the tap-handling logic and decides what to record. `Direction` is the Model. The Composable never logs directly. |
| **III. Single Purpose** | ✅ This is the command control surface; per-tap logging is the placeholder for the future command signal. No telemetry, analytics, accounts, or dashboards. Bluetooth deferred to a later feature (spec Assumptions). |
| **IV. Function Over Form** | ✅ Plain Material3 `IconButton`s with arrow icons in a simple layout. No theming/animation/responsive work beyond defaults. |
| **V. Mandatory Test Coverage** | ✅ Unit tests (JVM) assert the ViewModel records the correct `Direction` per tap, that repeats each record, and that one tap records only one direction. Instrumented Compose UI test (the integration-level coverage for this UI feature) asserts all four buttons exist, are tappable, and drive the ViewModel/logger correctly. No real Bluetooth layer exists yet, so the constitution's Bluetooth-integration-test clause does not apply to this feature. |

**Result**: PASS — no violations. Complexity Tracking table below is empty.

## Project Structure

### Documentation (this feature)

```text
specs/001-direction-buttons/
├── plan.md              # This file
├── spec.md              # Feature specification
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── ui-contract.md   # Phase 1 output — UI/ViewModel contract
├── checklists/
│   └── requirements.md  # Spec quality checklist (from /speckit-specify)
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
app/src/main/java/com/example/ambullrc/
├── MainActivity.kt                 # Hosts ControlScreen (replaces Greeting scaffold)
├── model/
│   └── Direction.kt                # enum: UP, DOWN, LEFT, RIGHT
├── viewmodel/
│   ├── ControlViewModel.kt         # onDirectionTapped(Direction); uses DirectionLogger
│   └── DirectionLogger.kt          # fun interface + Android Log-backed default
└── ui/
    ├── ControlScreen.kt            # Composable: four IconButtons wired to the ViewModel
    └── theme/                      # existing (Color.kt, Theme.kt, Type.kt)

app/src/test/java/com/example/ambullrc/
└── ControlViewModelTest.kt         # JVM unit tests with a fake DirectionLogger

app/src/androidTest/java/com/example/ambullrc/
└── ControlScreenTest.kt            # Compose UI instrumented test (four buttons + taps)
```

**Structure Decision**: Single Android module `:app`, packages organized flat by MVVM
role (`model`, `viewmodel`, `ui`) exactly as the constitution's Development Workflow
section prescribes. The existing `Greeting`/`GreetingPreview` sample in `MainActivity.kt`
is replaced by hosting `ControlScreen`. Existing `ui/theme` is reused unchanged.

## Complexity Tracking

> No constitution violations — no entries required.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| _(none)_  | _(n/a)_    | _(n/a)_                             |
