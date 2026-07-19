# Quickstart: Home UI & Branding Refresh

## Prerequisites

- `JAVA_HOME` set to Android Studio's bundled JBR (see root `CLAUDE.md`).
- A device/emulator to run against. Every scenario below works fine on the emulator (no Bluetooth
  radio needed — the app simply stays in the disconnected bucket, one of the states this feature's
  header-icon/hint-removal behavior must handle correctly anyway).

## Build & automated tests

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

./gradlew testDebugUnitTest            # unchanged — no unit-test-bearing logic in this feature
./gradlew connectedDebugAndroidTest    # ControlScreenTest, ConnectionStatusBarTest
./gradlew assembleDebug                # compile check
```

Expected: all unit and instrumented tests pass, per Constitution Principle V. (This feature adds no
new unit tests — see plan.md's Technical Context for why.)

## Manual validation (emulator or device, no ESP32 required)

1. `./gradlew installDebug` on a running emulator/device.
2. **App icon**: from the home screen/app drawer, confirm AmbullRC's icon shows the dark
   rounded-square background with the orange car/gamepad line-art — not the default Android robot
   placeholder (US4 Acceptance Scenario 1).
3. **Splash screen**: force-stop the app (`adb shell am force-stop com.example.ambullrc` or via
   device app-info), then cold-launch it. Confirm a brief dark (`#151210`) screen with the centered
   orange mark appears before the home screen UI loads, and that it dismisses on its own with no
   tap needed (US4 Acceptance Scenario 2). Re-open the app from recents (warm resume) and confirm
   the splash does **not** replay (US4 Acceptance Scenario 3).
4. **Header icon**: on the home screen, confirm a small brand icon appears at the top-left of the
   header, before the device name text, in whatever connection state the emulator settles into
   (disconnected, since there's no Bluetooth radio) (US3 Acceptance Scenario 1). Confirm the status
   pill and Retry button are still fully visible and not wrapped/pushed off-screen (US3 Acceptance
   Scenario 2).
5. **D-pad size**: look at the four directional buttons. Confirm they are visibly larger than
   before (filling most of the width/height between the header and the log panel) and that no
   button, the center hub, or their disabled/dimmed styling overlaps the header bar or the log panel
   strip (US1 Acceptance Scenario 1 & 2, SC-001).
6. **Hint text**: since the emulator has no Bluetooth radio, the app stays disconnected — confirm
   the "Waiting for connection to enable controls" hint still appears below the D-pad (US2
   Acceptance Scenario 2, unchanged case).
7. Rotate the device/emulator. Confirm the header, resized D-pad, and log panel all still fit
   without overlap (existing behavior, unaffected by this feature).

## On-device validation (physical phone + paired ESP32)

Once a real ESP32 is reachable (see `specs/002-esp32-bluetooth-connection/quickstart.md` for
pairing):

1. Install on the phone and open the app. Confirm the enlarged D-pad becomes fully interactive
   (undimmed) once connected, and that no "Hold a direction to drive" hint appears anywhere in the
   connected state (US2 Acceptance Scenario 1, US1 Acceptance Scenario 3).
2. Press and hold each of the larger direction buttons in turn. Confirm each still shows its
   pressed highlight for exactly as long as held and sends commands normally (unchanged command
   logic — only size changed).
3. Confirm the header icon and status pill remain both visible together while the device's real
   name is shown (US3 Acceptance Scenario 1 & 2, connected case).
