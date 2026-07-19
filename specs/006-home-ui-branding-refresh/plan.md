# Implementation Plan: Home UI & Branding Refresh

**Branch**: `006-home-ui-branding-refresh` | **Date**: 2026-07-19 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/006-home-ui-branding-refresh/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Four presentation-only changes to the existing single home screen: (1) the D-pad's fixed 76dp
button cells become responsive, expanding to fill the available control-region space; (2) the
connected-state "Hold a direction to drive" hint is deleted; (3) a small brand mark is added to the
leading edge of the existing header row; (4) the default Android-robot launcher icon is replaced
with the supplied car/gamepad adaptive icon, and a matching cold-launch splash screen is configured
using the platform's native Android 12+ splash-screen theme attributes. No ViewModel, Bluetooth, or
data-model changes — everything lives in `ui/`, `res/`, and the app theme.

## Technical Context

**Language/Version**: Kotlin 2.2.10

**Primary Dependencies**: Jetpack Compose (Material3) — already in use. No new dependency: the
splash screen uses the platform's native `android:windowSplashScreenBackground` /
`android:windowSplashScreenAnimatedIcon` theme attributes (available since API 31; app's `minSdk`
is already 33), not the `androidx.core:core-splashscreen` compat library, since that library only
exists to backport the behavior to pre-31 devices this app doesn't support.

**Storage**: N/A — no state, no persistence involved in this feature.

**Testing**: Jetpack Compose UI testing (`createComposeRule`, `app/src/androidTest`) for the D-pad
sizing/hint-removal and header-icon changes — same tooling already used for features 001–005. No
unit tests are added: nothing in this feature touches ViewModel or other pure-logic code (the app
icon and splash screen are Android resources/theme configuration with no testable Kotlin logic;
verified via the quickstart's manual/visual steps instead, consistent with how Compose's Material3
theming choices were validated in feature 005).

**Target Platform**: Android, minSdk 33 / targetSdk 36 (existing app configuration, unchanged).

**Project Type**: Mobile app — single Android module, existing MVVM package split
(`model` / `data` / `viewmodel` / `ui`).

**Performance Goals**: N/A — no new runtime computation; button sizing is resolved once during
layout via existing Compose measurement (`BoxWithConstraints`/`weight`), not per-frame logic.

**Constraints**: No new dependencies (Constitution Principle IV). No new architectural layer — the
adaptive icon and splash screen are pure Android resource/theme configuration (drawables + a
`themes.xml` addition), not a custom splash `Activity` or rendering surface. The exact colors used
must reuse the existing `Background` (`#151210`) / `Accent` (`#FF9A5A`) tokens already defined in
`ui/theme/Color.kt` rather than introducing new hardcoded hex values (FR-009) — confirmed by
pixel-sampling the four supplied PNGs (research.md Decision 3).

**Scale/Scope**: Exactly the four areas named in the spec (D-pad sizing, hint removal, header icon,
app icon + splash). Still a single screen, single Android module, no navigation, no settings.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Status |
|---|---|---|
| I. Simplicity & YAGNI | Splash screen uses the native API directly (no compat library) since `minSdk` already exceeds its floor — adding `core-splashscreen` would be a dependency with zero behavioral benefit here. Icon layers are added as raster bitmap drawables (the assets as supplied) rather than hand-converting them to vector paths — simplest path that satisfies FR-006, no speculative vector-authoring work. | PASS |
| II. MVVM Architecture | No ViewModel touched. `ControlScreen`/`ConnectionStatusBar` stay stateless Composables (state + callbacks in); sizing is resolved via layout constraints, not new UI state. | PASS |
| III. Single Purpose | Purely cosmetic/branding changes to the existing single screen; no new capability, no scope growth. | PASS |
| IV. Delightful UX, Simple Implementation | Larger, easier-to-hit controls and cohesive branding *are* the delight outcome here, achieved entirely through Compose layout primitives (`BoxWithConstraints`/`weight`/`fillMaxSize`) and native Android theme attributes — no bespoke animation framework, no third-party design/icon library. | PASS |
| V. Mandatory Test Coverage | Instrumented tests cover the two behavior-bearing changes (D-pad fills its region without overlap; hint text is gone when connected; header icon renders). The icon/splash-screen change has no Kotlin logic to unit-test — verified visually per quickstart.md, matching how feature 005 validated its fixed-palette theming choice. | PASS |

No violations — Complexity Tracking table not needed.

## Project Structure

### Documentation (this feature)

```text
specs/006-home-ui-branding-refresh/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/
│   └── ui-contract.md   # Phase 1 output (/speckit-plan command)
├── assets/              # supplied source PNGs (icon layers, flat icon, splash mock)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
app/src/main/
├── java/com/example/ambullrc/ui/
│   ├── ControlScreen.kt          # MODIFIED — CellSize fixed dp replaced by BoxWithConstraints-
│   │                             #   derived size filling the control region; hint text removed
│   │                             #   for the connected case
│   └── ConnectionStatusBar.kt    # MODIFIED — leading brand icon added to the existing header Row
├── res/
│   ├── drawable-nodpi/
│   │   ├── ic_launcher_background.png  # NEW — replaces drawable/ic_launcher_background.xml
│   │   └── ic_launcher_foreground.png  # NEW — replaces drawable/ic_launcher_foreground.xml;
│   │                                    #   also reused directly as the header brand icon and as
│   │                                    #   the splash screen's animated icon
│   ├── drawable/                       # ic_launcher_background.xml / ic_launcher_foreground.xml
│   │                                    #   REMOVED (superseded by the -nodpi PNGs above)
│   ├── mipmap-anydpi/
│   │   ├── ic_launcher.xml              # UNCHANGED — still references ic_launcher_background /
│   │   └── ic_launcher_round.xml        #   ic_launcher_foreground by name, now resolving to PNGs
│   └── values/
│       └── themes.xml                   # MODIFIED — adds windowSplashScreenBackground /
│                                         #   windowSplashScreenAnimatedIcon to Theme.AmbullRC
└── ... (mipmap-mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi ic_launcher*.webp — left as-is; superseded at
       runtime by the adaptive icon's background/foreground layers on API 26+, which this app's
       minSdk 33 always satisfies)

app/src/androidTest/java/com/example/ambullrc/
├── ControlScreenTest.kt          # MODIFIED — asserts buttons exceed the old fixed size and stay
│                                  #   within the control region; asserts hint text is gone when
│                                  #   connected
└── ConnectionStatusBarTest.kt    # MODIFIED — asserts the header icon is present in every state
```

**Structure Decision**: Extends the existing single-module Android app in place — no new source
sets, no new modules, no new packages. Only two Kotlin files change (`ControlScreen.kt`,
`ConnectionStatusBar.kt`); everything else is Android resources (drawables, theme XML). The
supplied icon/splash PNGs are used directly as bitmap drawables rather than hand-converted to
vector paths (see research.md Decision 1) — simplest path, no speculative asset-authoring work.

## Post-Design Constitution Re-check

Phase 0/1 design (research.md, data-model.md, contracts/, quickstart.md) introduced no new
dependency, no persistence, no Android-framework-bound seam beyond what already exists, and no
capability beyond FR-001..009. The splash screen and adaptive icon are both pure platform
resource/theme configuration; the D-pad resize and header icon are both plain Compose layout
changes to already-stateless Composables. The Constitution Check table above still holds unchanged
after design — no new violations surfaced.

## Complexity Tracking

Not applicable — no Constitution Check violations.
