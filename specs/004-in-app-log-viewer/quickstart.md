# Quickstart: In-App Log Viewer Widget

## Prerequisites

- `JAVA_HOME` set to Android Studio's bundled JBR (see root `CLAUDE.md`).
- A device/emulator to run against. The widget itself needs no Bluetooth hardware to validate — only
  the on-device hardware smoke checks below need a paired ESP32.

## Build & automated tests

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

./gradlew testDebugUnitTest            # DebugLogTest, ConnectionViewModelTest, ControlViewModelTest
./gradlew connectedDebugAndroidTest    # MainActivityLogWidgetTest (+ existing Compose UI tests)
./gradlew assembleDebug                # compile check
```

Expected: all unit and instrumented tests pass, per Constitution Principle V.

## Manual validation (emulator or device, no ESP32 required)

1. `./gradlew installDebug` on a running emulator/device.
2. Launch AmbullRC. Confirm a small scrollable panel is visible directly below the four direction
   arrows (FR-001).
3. Tap any direction button. Confirm a new line appears in the panel describing the tap's outcome
   (US1 Acceptance Scenario 1) — on the emulator (no Bluetooth radio) this will read as a "dropped"
   outcome, which is itself the correct, expected behavior (spec Edge Cases / feature 003 US2).
4. Tap several different directions in a row. Confirm each appears as its own entry, oldest to newest,
   and the panel auto-scrolls so the newest is visible (US1 Acceptance Scenario 3, US2).
5. Rotate the device/emulator. Confirm the panel's existing entries are still present after rotation.

## On-device validation (physical phone + paired ESP32)

Once a real ESP32 is reachable (see `specs/002-esp32-bluetooth-connection/quickstart.md` for pairing):

1. Install on the phone and open the app. Confirm connection-state messages appear in the widget as
   the app auto-connects (e.g. `"Connecting to ESP32…"` then `"Connected"`) — this gives on-screen
   confirmation of feature 002's auto-connect without needing `adb logcat`.
2. Tap each direction button. Confirm each shows `"<DIRECTION> -> sent"` in the widget — this is a
   more convenient on-device alternative to feature 003's T011 smoke check (which used the ESP32's
   serial monitor instead).
3. Power off / move the ESP32 out of range mid-session. Confirm a `"Connection lost"` entry appears,
   and subsequent taps show `"<DIRECTION> -> dropped (not connected)"` rather than crashing the app.
