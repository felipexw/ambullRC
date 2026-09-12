# Implementation Plan: Lights Toggle Command

**Branch**: `007-lights-toggle-command` | **Date**: 2026-09-11 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/007-lights-toggle-command/spec.md`

## Summary

A new button, placed in the D-pad's bottom-left corner cell (between Left and Down), lets the user
toggle the RC's lights. Unlike the directional buttons, this is a plain tap, and the button's icon
never changes optimistically — it only ever reflects what the ESP32 itself last confirmed. While
connected, the app polls the ESP32 for its actual light state on a fixed interval (a plain-text
`LIGHTS?` query, parsed from a `LIGHT_ON`/`LIGHT_OFF` reply on the same shared stream reader that
already detects disconnects); a tap sends `LIGHTS_ON`/`LIGHTS_OFF` (the opposite of the last confirmed
state) but only the next poll response actually moves the icon. `ControlViewModel` gains this logic
directly (no new ViewModel); `Esp32Connection` gains one new `lightState: StateFlow<Boolean?>`
property, reusing the existing `send`. This is the app's first two-way (read-back) communication with
the ESP32, and its first third command class beyond steering/throttle — both now explicitly permitted
by the constitution (v2.1.0, amended for this feature).

## Technical Context

**Language/Version**: Kotlin 2.2.10 (JVM target 11) — unchanged.

**Primary Dependencies**: Adds `androidx.compose.material:material-icons-extended` (for
`Icons.Filled.Lightbulb`/`Icons.Outlined.Lightbulb` — see research.md; no icon close enough exists in
the currently-included `material-icons-core`). Otherwise reuses `androidx.lifecycle:lifecycle-viewmodel-ktx`
and `kotlinx-coroutines-android`, already present.

**Storage**: N/A — no persistence, per spec.md Assumptions.

**Testing**: JUnit4 + `kotlinx-coroutines-test` (`app/src/test`, extending `ControlViewModelTest` and
`FakeEsp32Connection`) and Compose UI Test (`app/src/androidTest`, extending `ControlScreenTest` and
its own `FakeEsp32Connection` copy). Both already used by the project; no new test libraries.

**Target Platform**: Android, minSdk 33 / targetSdk 36 — unchanged.

**Project Type**: Mobile app (single Android module `:app`) — unchanged.

**Performance Goals**: Icon reflects a confirmed state change within one poll interval after a tap
(SC-001) — satisfied by a 500ms poll cadence (research.md), independent of and non-blocking toward
the existing 100ms directional-repeat stream (SC-003).

**Constraints**: Stay minimal per the constitution (v2.1.0) — plain newline-delimited text for the
three new messages, no request/response correlation, no acknowledgment beyond the existing polling
model, no new dependency beyond the one icon pack. The single existing socket reader
(`awaitDisconnect`'s loop) is reused/adapted rather than adding a second concurrent reader — see
research.md. No real ESP32 needed to run the automated suite (extended fake seam).

**Scale/Scope**: One new seam property (`lightState`), one changed seam method body
(`awaitDisconnect`'s read loop now parses lines), `connect()` resets `lightState`; `ControlViewModel`
gains `setConnected`/`onLightsTapped`/`lightsOn`/`lightsEnabled`; `ControlScreen` gains one button;
`MainActivity` gains one `LaunchedEffect` call. No new production files; extends
`Esp32Connection`/`BluetoothEsp32Connection`/`FakeEsp32Connection`/`ControlViewModel`/`ControlScreen`/
`MainActivity` plus their existing tests.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Assessment |
|-----------|------------|
| **I. Simplicity & YAGNI** | ✅ Reuses the existing `send` method and the existing single stream reader instead of adding new abstractions; no request/response correlation; poll interval is a hardcoded constant, not configurable. The one new dependency (`material-icons-extended`) is scoped to a single icon pair, justified in research.md because no adequate icon exists in the already-included set — matches Development Workflow's "adopt a new dependency only when the task genuinely cannot be done reasonably without it." |
| **II. MVVM Architecture** | ✅ All new logic (polling, toggle decision, enablement) lives in `ControlViewModel`; `ControlScreen` stays stateless, reading `lightsOn`/`lightsEnabled` and forwarding taps, same as the existing `DirectionButton`s. `Esp32Connection`/`BluetoothEsp32Connection` remain the Model/hardware layer. `MainActivity`'s one new line (`LaunchedEffect(connected) { controlViewModel.setConnected(connected) }`) only forwards a value the View already computes — no business logic in the View. |
| **III. Single Purpose** | ✅ Lights on/off is still direct command transmission to the ESP32 (now with one narrow read-back), not telemetry/analytics/dashboards. Constitution v2.1.0 explicitly scopes this in. |
| **IV. Delightful UX, Simple Implementation** | ✅ The icon swap uses a standard Material icon pair (no custom rendering); disabled state reuses the existing `alpha` dimming pattern already used by the directional buttons — no new visual-effect layer. |
| **V. Mandatory Test Coverage** | ✅ `ControlViewModelTest` gains unit coverage for polling/enablement/confirmed-only-update using `kotlinx-coroutines-test` virtual time (mirrors the existing directional-hold tests); `ControlScreenTest` gains instrumented coverage for the new button's rendering/state. Both fakes (`test`, `androidTest`) gain `lightState` support with no real hardware. |

**Result**: PASS. Constitution v2.1.0 (amended 2026-09-11, before this plan) explicitly permits the
third actuator and the narrow state read-back this feature needs; no unresolved gate failures. The
one new dependency is justified above and does not require a Complexity Tracking entry (no principle
is violated — it is an explicitly-anticipated, narrowly-scoped exception under Development Workflow
guidance, not a deviation from Principles I–V).

## Project Structure

### Documentation (this feature)

```text
specs/007-lights-toggle-command/
├── plan.md              # This file
├── spec.md              # Feature specification
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md         # Phase 1 output
├── contracts/
│   └── lights-contract.md   # Phase 1 output — extended seam + ControlViewModel + ControlScreen contract
├── checklists/
│   └── requirements.md  # Spec quality checklist (from /speckit-specify)
└── tasks.md              # Phase 2 output (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
app/src/main/java/com/example/ambullrc/
├── MainActivity.kt                     # + LaunchedEffect(connected) { controlViewModel.setConnected(connected) }
├── model/
│   ├── Direction.kt                    # existing, unchanged
│   ├── ConnectionState.kt              # existing, unchanged
│   └── Esp32Connection.kt              # + val lightState: StateFlow<Boolean?>
├── data/
│   ├── Esp32Config.kt                  # existing, unchanged
│   └── BluetoothEsp32Connection.kt     # connect() resets lightState; awaitDisconnect() parses lines
├── viewmodel/
│   ├── ControlViewModel.kt             # + setConnected/onLightsTapped/lightsOn/lightsEnabled + poll loop
│   ├── DirectionLogger.kt              # existing, unchanged
│   ├── DebugLog.kt                     # existing, unchanged (reused for lights log entries)
│   └── ConnectionViewModel.kt          # existing, unchanged
└── ui/
    ├── ControlScreen.kt                # + lights button in the bottom-left corner cell
    └── ConnectionStatusBar.kt          # existing, unchanged

app/src/test/java/com/example/ambullrc/
├── FakeEsp32Connection.kt              # + lightState: MutableStateFlow<Boolean?>, reset in connect()
└── ControlViewModelTest.kt             # + lights polling/enablement/confirmed-only-update cases

app/src/androidTest/java/com/example/ambullrc/
├── FakeEsp32Connection.kt              # + same lightState addition as the unit-test fake
└── ControlScreenTest.kt                # + lights button rendering/state cases

gradle/libs.versions.toml                # + androidx-compose-material-icons-extended alias
app/build.gradle.kts                     # + implementation(libs.androidx.compose.material.icons.extended)
```

**Structure Decision**: Single module `:app`, flat MVVM packages — unchanged. This feature only
extends existing files (as listed above) and adds no new production or test files, consistent with
Principle I; the only new artifact is one dependency declaration.

## Complexity Tracking

No constitution violations — no entries required. (The one new dependency is addressed in the
Constitution Check row above, not here, since it does not violate any of Principles I–V.)

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| _(none)_  | _(n/a)_    | _(n/a)_                             |
