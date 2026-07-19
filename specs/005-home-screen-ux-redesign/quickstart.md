# Quickstart: Home Screen UX Redesign

## Prerequisites

- `JAVA_HOME` set to Android Studio's bundled JBR (see root `CLAUDE.md`).
- A device/emulator to run against. All scenarios below except the on-device Bluetooth checks work
  fine on the emulator (no Bluetooth radio needed — connection will simply stay/return to the
  "disconnected" bucket, which is one of the three states being validated anyway).

## Build & automated tests

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

./gradlew testDebugUnitTest            # DebugLogTest, ConnectionViewModelTest, ControlViewModelTest
./gradlew connectedDebugAndroidTest    # ConnectionStatusBarTest, ControlScreenTest, DebugLogPanelTest
./gradlew assembleDebug                # compile check
```

Expected: all unit and instrumented tests pass, per Constitution Principle V.

## Manual validation (emulator or device, no ESP32 required)

1. `./gradlew installDebug` on a running emulator/device.
2. Launch AmbullRC. Confirm the header shows a colored status pill (not plain text) with a label
   and dot, and — since the emulator has no Bluetooth radio — it settles into the disconnected
   color/label with a Retry button visible (US1 Acceptance Scenario 3).
3. Tap Retry. Confirm the pill switches to the connecting color with a pulsing dot, then back to
   disconnected with Retry showing again once the attempt fails (US1 Acceptance Scenario 1 & 4).
4. Look at the D-pad. Confirm all four direction controls appear dimmed, and tapping/holding one
   has no visible effect and the hint line reads the "waiting for connection" text (US2 Acceptance
   Scenario 3 & 4).
5. Look at the log panel. Confirm it starts collapsed as a thin strip showing a live
   `"LOGS · <count>"` count. Tap it — confirm it expands smoothly to show the scrolling entry list
   (US3 Acceptance Scenario 1 & 2).
6. In the expanded list, confirm entries from steps 2-3 (connecting/disconnected/retry events) are
   visible with a timestamp, a colored category tag, and a message; connection-failure entries
   render in the error text color (US3 Acceptance Scenario 5).
7. Drag the panel's handle down past the midpoint and release. Confirm it snaps fully closed. Drag
   it up past the midpoint and release. Confirm it snaps fully open (US3 Acceptance Scenario 3).
8. Rotate the device/emulator. Confirm the header, D-pad, and log panel states all survive
   rotation (existing behavior, unchanged by this feature).

## On-device validation (physical phone + paired ESP32)

Once a real ESP32 is reachable (see `specs/002-esp32-bluetooth-connection/quickstart.md` for
pairing):

1. Install on the phone and open the app. Confirm the status pill goes connecting (pulsing amber)
   → connected (steady green) as the app auto-connects, and the header shows the device's real
   name (US1 Acceptance Scenario 1 & 2).
2. Confirm the D-pad is no longer dimmed once connected, and the hint line switches to "Hold a
   direction to drive" (US2 Acceptance Scenario 3-4, connected case).
3. Press and hold each direction control in turn. Confirm each shows its distinct pressed
   highlight for exactly as long as it's held, and releases cleanly (US2 Acceptance Scenario 1-2).
4. Expand the log panel and confirm `"<DIRECTION> -> sent"` entries appear tagged with the sent
   (`TX`) color as you drive (matches feature 003's T011 smoke check, now with color-coding).
5. Power off / move the ESP32 out of range mid-session. Confirm the header pill switches back to
   the disconnected color/label with Retry, the D-pad dims again, and a connection-lost entry
   appears in the log (spec Edge Cases: mid-press disconnect reverts the control's pressed state).
