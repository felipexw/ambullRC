# Implementation Plan: Horn Sound Command (Played by the ESP32)

**Branch**: `008-horn-sound-command` | **Date**: 2026-09-15 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/008-horn-sound-command/spec.md`

## Summary

A new tap button, placed in the D-pad's bottom-right corner cell (between Right and Down,
mirroring the lights button's opposite corner), lets the operator trigger the ESP32's own built-in
horn/siren sound. This feature went through three corrections before reaching this shape: a
feasibility check found "stream whatever's playing on the phone" infeasible for a normal Android
app; a correction moved the sound onto the ESP32 entirely; and a `/speckit-clarify` pass then
established that the app must not wait for or read back anything from the ESP32 at all — the
control simply re-enables itself on a fixed 1500ms local timer after each successful trigger. The
result needs **no changes to `Esp32Connection` whatsoever** — the horn reuses the exact `send`
method direction/lights commands already share, and all new logic (the cooldown timer and its
derived availability) lives entirely in `ControlViewModel`, the same place
`pressedDirections`/`repeatJobs` already live for the same kind of reason.

## Technical Context

**Language/Version**: Kotlin 2.2.10 (JVM target 11) — unchanged.

**Primary Dependencies**: No new dependency. Reuses `androidx.compose.material:material-icons-extended`
(already added in feature 007) for `Icons.Filled.Campaign` (confirmed present in the already-
included `.aar`). No `android.media`/`android.bluetooth` A2DP API, no new permission — this
feature only adds one outbound text command over the existing RFCOMM/SPP path.

**Storage**: N/A — no persistence, per spec.md Assumptions.

**Testing**: JUnit4 + `kotlinx-coroutines-test` (`app/src/test`, extending `ControlViewModelTest`)
and Compose UI Test (`app/src/androidTest`, extending `ControlScreenTest`). Both already used by
the project; no new test libraries and no changes needed to either `FakeEsp32Connection` (the
seam itself is untouched).

**Target Platform**: Android, minSdk 33 / targetSdk 36 — unchanged.

**Project Type**: Mobile app (single Android module `:app`) — unchanged.

**Performance Goals**: Horn control shows "sounding" immediately on a successful send and returns
to "ready" exactly `hornCooldownMillis` (1500ms) later (SC-001, SC-005, per Clarifications) —
driven by a single tracked coroutine `Job`, not a poll loop (unlike feature 007's 500ms lights
poll, there's nothing to poll here). Directional-repeat cadence (100ms, feature 003) must remain
unaffected by horn taps at any point (SC-002/SC-003) — satisfied by the same already-proven
concurrency behavior feature 007 established between direction and lights commands sharing one
`Esp32Connection.send`.

**Constraints**: Stay minimal per the constitution (v2.3.0) — one new outbound message
(`"HORN\n"`), zero new inbound messages, zero seam/interface changes. The 1500ms cooldown is a
hardcoded constant (`HORN_COOLDOWN_MS`), exposed as a constructor parameter with that default
purely for test-time virtual-time control (mirrors feature 007's `lightsPollIntervalMillis`
precedent), not as a product-configurable setting. No new permission (no microphone, no audio
APIs — sound generation is entirely the ESP32's concern). No real ESP32 needed to run the
automated suite.

**Scale/Scope**: One changed file (`ControlViewModel` gains `hornPlaying`/`hornAvailable`/
`onHornTapped`, a `hornResetJob`, and a `setConnected` addition to reset the cooldown on
disconnect), one changed UI file (`ControlScreen` gains one button). No `Esp32Connection`,
`BluetoothEsp32Connection`, or `FakeEsp32Connection` changes at all, and no `MainActivity` changes
beyond what feature 007 already wired. This is the smallest-footprint feature in the project's
history — smaller even than the previous (now-superseded) plan revision for this same feature.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Assessment |
|-----------|------------|
| **I. Simplicity & YAGNI** | ✅ Zero seam/interface changes; zero new dependency; zero new permission; the cooldown is a single hardcoded constant and one tracked `Job`, not a state machine or polling loop. Per the Clarifications session, the app explicitly does *not* build any ESP32 read-back mechanism for this feature — the simplest thing that satisfies the requirement. |
| **II. MVVM Architecture** | ✅ All new logic (cooldown timing, availability derivation) lives in `ControlViewModel`, alongside its existing `pressedDirections`/`repeatJobs` bookkeeping; `ControlScreen` stays stateless, reading `hornPlaying`/`hornAvailable` and forwarding taps, same as `LightsButton`. `Esp32Connection` remains untouched — there is no Model-layer change at all for this feature. |
| **III. Single Purpose** | ✅ The constitution (v2.3.0) names the horn as another discrete accessory command alongside lights — direct command transmission, no telemetry/analytics/dashboards, and (per this plan) not even a read-back this time, just a one-way trigger like feature 003's direction commands. |
| **IV. Delightful UX, Simple Implementation** | ✅ Reuses the existing `DirectionButton`/`LightsButton` visual vocabulary (`Accent` for active, dimmed `alpha` for disabled) — no custom rendering, no new animation framework. Placement (bottom-right corner, mirroring lights' bottom-left) reuses the existing 3×3 grid from feature 006 as-is. |
| **V. Mandatory Test Coverage** | ✅ `ControlViewModelTest` gains unit coverage for the cooldown timing (using `kotlinx-coroutines-test` virtual time — no real delay), the gating logic, the disconnect-mid-cooldown reset, and the existing direction/lights/horn concurrency guarantee extended to a third command. `ControlScreenTest` gains instrumented coverage for the new button's position and three visual states. Neither needs `FakeEsp32Connection` changes since the seam itself is untouched. |

**Result**: PASS. Constitution v2.3.0 explicitly names the horn as an in-scope discrete accessory;
no unresolved gate failures and no Complexity Tracking entries are needed — this revision is
strictly simpler than the one it supersedes (no seam changes at all, versus the prior revision's
two new `Esp32Connection` properties and reader-loop changes).

## Project Structure

### Documentation (this feature)

```text
specs/008-horn-sound-command/
├── plan.md              # This file
├── spec.md              # Feature specification
├── research.md          # Phase 0 output
├── data-model.md         # Phase 1 output
├── quickstart.md         # Phase 1 output
├── contracts/
│   └── horn-contract.md  # Phase 1 output — ControlViewModel + ControlScreen contract (Esp32Connection unchanged)
├── checklists/
│   └── requirements.md   # Spec quality checklist (from /speckit-specify)
└── tasks.md               # Phase 2 output (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
app/src/main/java/com/example/ambullrc/
├── MainActivity.kt                     # existing, unchanged
├── model/
│   ├── Direction.kt                    # existing, unchanged
│   ├── ConnectionState.kt              # existing, unchanged
│   └── Esp32Connection.kt              # existing, unchanged — this feature adds no seam surface
├── data/
│   ├── Esp32Config.kt                  # existing, unchanged
│   └── BluetoothEsp32Connection.kt     # existing, unchanged
├── viewmodel/
│   ├── ControlViewModel.kt             # + HORN_COOLDOWN_MS, hornPlaying/hornAvailable/onHornTapped, hornResetJob, setConnected reset
│   ├── DirectionLogger.kt              # existing, unchanged
│   ├── DebugLog.kt                     # existing, unchanged (reused for horn log entries)
│   └── ConnectionViewModel.kt          # existing, unchanged
└── ui/
    ├── ControlScreen.kt                # + horn button in the bottom-right corner cell
    └── ConnectionStatusBar.kt          # existing, unchanged

app/src/test/java/com/example/ambullrc/
├── FakeEsp32Connection.kt              # existing, unchanged
└── ControlViewModelTest.kt             # + horn cooldown/gating/disconnect-reset/concurrency cases

app/src/androidTest/java/com/example/ambullrc/
├── FakeEsp32Connection.kt              # existing, unchanged
└── ControlScreenTest.kt                # + horn button position/state rendering cases
```

**Structure Decision**: Single module `:app`, flat MVVM packages — unchanged. This feature touches
only `ControlViewModel`/`ControlScreen` and their existing tests — no new production files, no
seam changes, the smallest structural footprint of any feature so far.

## Complexity Tracking

No constitution violations — no entries required.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| _(none)_  | _(n/a)_    | _(n/a)_                             |
