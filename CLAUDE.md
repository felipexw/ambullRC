# CLAUDE.md

Guidance for Claude Code (and other AI agents) working in this repository. See also
[`AGENTS.md`](AGENTS.md), which mirrors this content for tool-agnostic agents.

## What this project is

AmbullRC is an Android **Bluetooth remote control for an ESP32** that drives one servomotor (rear
steering) and one DC motor (engine). It is a personal **learning side-project**, not production. Its
single purpose is to send commands (signals) to the ESP32.

## Non-negotiable principles

These come from `.specify/memory/constitution.md` (v2.0.0). Read it before non-trivial changes.

1. **Simplicity & YAGNI (NON-NEGOTIABLE)** — build the simplest thing that works. No speculative
   abstractions, no features that aren't asked for.
2. **MVVM architecture** — keep the flat package split: `model`, `data`, `viewmodel`, `ui`.
3. **Single purpose** — the app only transmits commands to the ESP32. Don't add unrelated scope.
4. **Delightful UX, simple implementation** — UX/look-and-feel MUST be polished and delightful
   (clear feedback, smooth motion, coherent theming), but achieve it with idiomatic Compose/
   Material 3 primitives only — no bespoke animation frameworks or new architectural layers.
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
  commands), 004 (in-app log viewer widget), 005 (home screen UX redesign), and 006 (home UI &
  branding refresh) are implemented. Automated tests (unit + instrumented) pass for all six.
- **Feature 006** (`specs/006-home-ui-branding-refresh/`) made four presentation-only changes: the
  D-pad's fixed 76dp cells (`ui/ControlScreen.kt`) now use `BoxWithConstraints`/weighted rows-cells
  to fill the whole control region; the connected-state "Hold a direction to drive" hint was
  removed (disconnected hint unchanged); a small brand icon was added to the header's leading edge
  (`ui/ConnectionStatusBar.kt`, reusing `R.drawable.ic_launcher_foreground`); and the default
  Android-robot launcher icon was replaced with the supplied orange car/gamepad mark (raster
  `res/drawable-nodpi/ic_launcher_*.png` layers) with a matching cold-launch splash screen wired up
  via native Android 12+ theme attributes (`android:windowSplashScreenBackground`/
  `windowSplashScreenAnimatedIcon` in `res/values/themes.xml`) — no new dependency. Verified
  visually on the emulator (T012) and, on 2026-07-19, on a physical phone connected live to the
  ESP32 (**T013**, now done): enlarged D-pad, header icon, hint removal, and the "Connected" pill
  all confirmed, with real TX log entries showing directional commands actually being sent.
- **2026-07-19 post-ship tweaks** (small follow-up polish on feature 006, done ad hoc rather than
  through a new spec): the header brand icon (`ui/ConnectionStatusBar.kt`) grew from 28dp to 40dp;
  the cold-launch splash screen (`MainActivity.kt`) now holds an extra ~1s via
  `splashScreen.setOnExitAnimationListener` (the platform `android.window.SplashScreen` API has no
  compat-library-style `setKeepOnScreenCondition`, so the exit-animation listener delays
  `SplashScreenView.remove()` instead); the decorative center-hub circle in the D-pad
  (`ui/ControlScreen.kt`'s `CenterHub`) was deleted outright (that middle cross cell is now blank);
  and the four directional buttons are now perfect squares — `ControlScreen` wraps the grid in a
  single `BoxWithConstraints`, computes `cellSize = (minOf(maxWidth, maxHeight) - gaps) / 3`, and
  centers the resulting square 3x3 grid, replacing the old per-row/per-cell `weight(1f)` approach
  that could yield rectangular (non-square) cells on non-square available regions. Verified on the
  physical phone alongside the T013 check above; unit + all 41 instrumented tests still pass.
- **Feature 005** redesigned all three home-screen areas per the design handoff in
  `specs/005-home-screen-ux-redesign/`: a color-coded/animated connection status pill (header), a
  cross-layout D-pad with pressed/disabled visual states (controls), and a tap-or-drag collapsible
  log sheet with timestamped, category/level-colored entries (log panel). Also introduced a fixed
  dark theme (`ui/theme/Color.kt`/`Theme.kt` — dynamic Material You color is now off) and gave log
  entries structure (`viewmodel/LogEntry.kt`: timestamp + category + level) since color-coding
  needed more than the old plain-string entries. Verified visually on the emulator (T022) and, on
  2026-07-19, on a physical phone against the real ESP32 (**T023**, now done): the "Connected" pill
  color transition and press-driven directional commands were confirmed live (see T013 note above).
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
