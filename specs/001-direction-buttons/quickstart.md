# Quickstart & Validation: Direction Buttons Screen

How to build, run, and validate that the feature meets its spec. Implementation details
live in `tasks.md` / the source; this is a run-and-verify guide.

## Prerequisites

- JDK 11+ and the Android SDK (the project targets compileSdk 36, minSdk 33).
- An Android emulator or device running API 33+ for the app and the instrumented UI test.
- Use the bundled Gradle wrapper (`./gradlew`) from the repo root.

## Build

```bash
./gradlew :app:assembleDebug
```

Expected: build succeeds. If it fails resolving `androidx.compose.material.icons.filled.*`,
apply the icons fallback from [research.md](./research.md) (add `material-icons-core`) and
rebuild — this is the only anticipated dependency adjustment.

## Run the app (manual smoke check)

```bash
./gradlew :app:installDebug
# then launch AmbullRC on the device/emulator
```

Validate against the spec:

- **SC-001 / US1**: The screen shows exactly four buttons — up, down, left, right — each
  with its matching arrow icon.
- **US2**: Tap each button; open Logcat and confirm one log line per tap identifying the
  tapped direction. Tap a button several times and confirm one log line per tap.

```bash
# Watch tap logs (adjust the tag/filter to the implementation's log tag):
adb logcat | grep -i direction
```

## Automated validation

### Unit tests (JVM — ViewModel logic)

```bash
./gradlew :app:testDebugUnitTest
```

Covers, via a fake `DirectionLogger` (see [contracts/ui-contract.md](./contracts/ui-contract.md)):

- Each `onDirectionTapped(UP|DOWN|LEFT|RIGHT)` logs the matching direction (FR-004).
- N taps produce N ordered records (FR-005).
- One tap logs exactly one record, never another direction (FR-006).

Maps to **SC-002** and **SC-003**.

### Instrumented UI test (device/emulator — screen + taps)

```bash
./gradlew :app:connectedDebugAndroidTest
```

`ControlScreenTest` asserts:

- All four buttons exist, selected by the content descriptions / test tags in the
  contract (FR-001, FR-002, FR-003 → **SC-001**).
- Clicking each button drives the fake logger with the correct direction, with no
  cross-firing (**SC-002**, **SC-003**).

## Definition of done (constitution Principle V)

- [ ] `:app:testDebugUnitTest` passes (ViewModel unit tests).
- [ ] `:app:connectedDebugAndroidTest` passes (Compose UI test).
- [ ] Manual Logcat smoke check shows one record per tap, correct direction.
