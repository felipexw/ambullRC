---
description: "Task list for ESP32 Bluetooth Connection on Startup"
---

# Tasks: ESP32 Bluetooth Connection on Startup

**Input**: Design documents from `/specs/002-esp32-bluetooth-connection/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/connection-contract.md, quickstart.md

**Tests**: REQUIRED for this project. Per constitution Principle V, the Bluetooth layer must have
integration-level coverage against a fake/test double and the ViewModel logic must have unit tests,
so test tasks are included and are not optional.

**Organization**: Tasks are grouped by user story. US1 (auto-connect) and US2 (status visibility)
are Priority P1 and together form the MVP; US3 (recover from problems) is Priority P2.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- All paths are repo-relative

## Path Conventions

Single Android module `:app`:

- Main code: `app/src/main/java/com/example/ambullrc/`
- Unit tests (JVM): `app/src/test/java/com/example/ambullrc/`
- Instrumented tests: `app/src/androidTest/java/com/example/ambullrc/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Add the dependencies and manifest permission the feature needs before writing code.

- [X] T001 Add dependencies in `gradle/libs.versions.toml` and `app/build.gradle.kts`: `org.jetbrains.kotlinx:kotlinx-coroutines-android` and `androidx.lifecycle:lifecycle-viewmodel-ktx` as `implementation`, and `org.jetbrains.kotlinx:kotlinx-coroutines-test` as `testImplementation` (per research.md Decision 4); run `./gradlew :app:assembleDebug` to confirm resolution.
- [X] T002 Add `<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />` to `app/src/main/AndroidManifest.xml` (per research.md Decision 3).

**Checkpoint**: Project builds with the new deps and permission declared.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared model types, the Bluetooth seam, config, and the test double that ALL stories depend on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T003 [P] Create `ConnectionState` sealed interface (`Idle`, `Connecting`, `Connected`, `Failed(reason)`) and `FailureReason` enum (`PERMISSION_DENIED`, `BLUETOOTH_DISABLED`, `DEVICE_UNAVAILABLE`, `CONNECTION_LOST`, `ERROR`) in `app/src/main/java/com/example/ambullrc/model/ConnectionState.kt` per data-model.md.
- [X] T004 [P] Create `Esp32Connection` interface (`suspend connect()`, `suspend awaitDisconnect()`, `disconnect()`) and the `Esp32ConnectionException` sealed hierarchy (`BluetoothDisabledException`, `DeviceUnavailableException`, `LinkException`) in `app/src/main/java/com/example/ambullrc/model/Esp32Connection.kt` per contracts/connection-contract.md.
- [X] T005 [P] Create `Esp32Config` with `DEVICE_NAME` (default `"AmbullRC-ESP32"`) and `SPP_UUID` (`00001101-0000-1000-8000-00805F9B34FB`) in `app/src/main/java/com/example/ambullrc/data/Esp32Config.kt` per research.md Decision 2.
- [X] T006 Create `FakeEsp32Connection` test double implementing `Esp32Connection` in `app/src/test/java/com/example/ambullrc/FakeEsp32Connection.kt`: configurable to connect successfully or throw a chosen `Esp32ConnectionException`, a controllable `awaitDisconnect()` (via `CompletableDeferred`) to simulate a drop, and call counters for assertions (depends on T004).

**Checkpoint**: Shared model + seam + fake compile — user stories can proceed.

---

## Phase 3: User Story 1 - Auto-connect to the ESP32 on startup (Priority: P1) 🎯 MVP

**Goal**: On app launch the app automatically attempts the Bluetooth connection and reaches
`Connected` when the ESP32 is available, without any manual step.

**Independent Test**: With the ESP32 available, launch the app (permission granted) and confirm the
ViewModel state reaches `Connected` (asserted by the unit test; visible via US2's status bar).

### Tests for User Story 1 ⚠️

- [X] T007 [US1] Write JVM unit test `ConnectionViewModelTest` in `app/src/test/java/com/example/ambullrc/ConnectionViewModelTest.kt` using `FakeEsp32Connection` + `kotlinx-coroutines-test` (`Dispatchers.setMain` + `runTest`): assert `connect()` transitions `Idle → Connecting → Connected` on a successful fake connect (FR-001, FR-005), and that state is never `Connected` unless the fake's `connect()` returned successfully (SC-005).

### Implementation for User Story 1

- [X] T008 [US1] Implement `ConnectionViewModel` (extends `androidx.lifecycle.ViewModel`) in `app/src/main/java/com/example/ambullrc/viewmodel/ConnectionViewModel.kt`: constructor `(connection: Esp32Connection, connectTimeoutMillis: Long = 12_000, ioDispatcher: CoroutineDispatcher = Dispatchers.IO)`; expose `StateFlow<ConnectionState>` starting `Idle`; `connect()` sets `Connecting`, calls `connection.connect()` on `ioDispatcher` in `viewModelScope`, sets `Connected` on success; `onCleared()` calls `connection.disconnect()` (depends on T003, T004).
- [X] T009 [US1] Implement `BluetoothEsp32Connection` (real `Esp32Connection`) in `app/src/main/java/com/example/ambullrc/data/BluetoothEsp32Connection.kt`: resolve `BluetoothManager`/`BluetoothAdapter`; if adapter null/disabled throw `BluetoothDisabledException`; find the bonded device named `Esp32Config.DEVICE_NAME` else throw `DeviceUnavailableException`; open an RFCOMM socket to `Esp32Config.SPP_UUID` and `connect()`, mapping `IOException` on open → `DeviceUnavailableException`; `disconnect()` closes the socket idempotently (depends on T004, T005). (`awaitDisconnect()` implemented in T017.)
- [X] T010 [US1] Update `MainActivity` in `app/src/main/java/com/example/ambullrc/MainActivity.kt`: register a `BLUETOOTH_CONNECT` permission launcher (`ActivityResultContracts.RequestPermission`), provide `ConnectionViewModel` via `by viewModels { factory }` backed by `BluetoothEsp32Connection`, and on permission granted call `viewModel.connect()`; keep hosting the existing `ControlScreen` (depends on T008, T009).

**Checkpoint**: On launch with permission granted and ESP32 available, the ViewModel reaches `Connected`; T007 passes.

---

## Phase 4: User Story 2 - Know the current connection status (Priority: P1)

**Goal**: The operator can always see whether the app is connecting, connected, or not connected.

**Independent Test**: Render `ConnectionStatusBar` with each `ConnectionState` and confirm the
status text is correct and the Retry button appears only when failed.

### Tests for User Story 2 ⚠️

- [X] T011 [US2] Write instrumented Compose UI test `ConnectionStatusBarTest` in `app/src/androidTest/java/com/example/ambullrc/ConnectionStatusBarTest.kt`: for each `ConnectionState` (`Idle`, `Connecting`, `Connected`, `Failed(reason)`), set `ConnectionStatusBar` content and assert the `status_text` node shows the expected text, and assert the `btn_retry` node exists only for `Failed` (FR-004, US2, SC-002).

### Implementation for User Story 2

- [X] T012 [US2] Implement stateless `ConnectionStatusBar(state, onRetry, modifier)` composable in `app/src/main/java/com/example/ambullrc/ui/ConnectionStatusBar.kt`: render status text per state with `Modifier.testTag("status_text")`; show a Retry button (`testTag("btn_retry")`, content description "Retry") calling `onRetry` only when `state is ConnectionState.Failed`, per contracts/connection-contract.md (depends on T003).
- [X] T013 [US2] Update `MainActivity` in `app/src/main/java/com/example/ambullrc/MainActivity.kt` to `collectAsState()` the ViewModel `state` and place `ConnectionStatusBar(state, onRetry = viewModel::retry)` above the existing `ControlScreen` in the Scaffold (depends on T010, T012).

**Checkpoint**: The status bar reflects the live connection state; T011 passes. US1+US2 = demoable MVP.

---

## Phase 5: User Story 3 - Recover from connection problems (Priority: P2)

**Goal**: Failures (device off, Bluetooth off, permission denied, timeout) are reported within a
bounded time with a reason, the operator can retry, and mid-session drops are reflected.

**Independent Test**: Drive the ViewModel with a failing/dropping fake and confirm each `Failed`
reason, the timeout, retry recovery, and `CONNECTION_LOST`; tap Retry in the UI and confirm `onRetry`.

### Tests for User Story 3 ⚠️

- [X] T014 [US3] Extend `ConnectionViewModelTest` in `app/src/test/java/com/example/ambullrc/ConnectionViewModelTest.kt`: assert each exception maps to the right `Failed(reason)` (`BluetoothDisabledException`→`BLUETOOTH_DISABLED`, `DeviceUnavailableException`→`DEVICE_UNAVAILABLE`, other→`ERROR`); a never-completing connect times out to `Failed(DEVICE_UNAVAILABLE)` using virtual time (SC-003); `onPermissionDenied()` → `Failed(PERMISSION_DENIED)` without calling the seam's `connect()` (FR-003); `retry()` from `Failed` reaches `Connected` (FR-007, SC-004); after `Connected`, completing the fake's `awaitDisconnect()` → `Failed(CONNECTION_LOST)` (FR-008) (depends on T007).
- [X] T015 [US3] Extend `ConnectionStatusBarTest` in `app/src/androidTest/java/com/example/ambullrc/ConnectionStatusBarTest.kt`: assert tapping `btn_retry` in a `Failed` state invokes `onRetry`, and that `btn_retry` is absent in `Idle`/`Connecting`/`Connected` (FR-007) (depends on T011).

### Implementation for User Story 3

- [X] T016 [US3] Extend `ConnectionViewModel` in `app/src/main/java/com/example/ambullrc/viewmodel/ConnectionViewModel.kt`: wrap `connect()` in `withTimeout(connectTimeoutMillis)`; map caught `Esp32ConnectionException` subtypes and `TimeoutCancellationException` to `Failed(reason)` per data-model.md; add `retry()` (re-runs `connect()` from `Failed`) and `onPermissionDenied()` (`Failed(PERMISSION_DENIED)`); after reaching `Connected`, launch a `viewModelScope` job awaiting `connection.awaitDisconnect()` then setting `Failed(CONNECTION_LOST)` (depends on T008).
- [X] T017 [US3] Implement `BluetoothEsp32Connection.awaitDisconnect()` in `app/src/main/java/com/example/ambullrc/data/BluetoothEsp32Connection.kt`: block on the socket `inputStream.read()`, returning when it yields `-1` or throws `IOException`, discarding any bytes read (liveness only, per research.md Decision 6) (depends on T009).
- [X] T018 [US3] Update `MainActivity` in `app/src/main/java/com/example/ambullrc/MainActivity.kt`: on permission denied call `viewModel.onPermissionDenied()`; ensure the Retry action re-checks the `BLUETOOTH_CONNECT` permission (re-request if needed) before re-attempting the connection (depends on T013, T016).

**Checkpoint**: All failure/retry/drop paths behave per spec; T014 and T015 pass. Feature complete.

---

## Phase 6: Polish & Validation

**Purpose**: Run the full suite and the on-device smoke check from quickstart.md (Definition of Done).

- [X] T019 [P] Run `./gradlew :app:testDebugUnitTest` and `./gradlew :app:connectedDebugAndroidTest`; confirm all unit and instrumented tests pass (quickstart.md Definition of Done).
- [X] T020 On-device smoke check per quickstart.md using a real phone with a bonded ESP32 (SerialBT sketch advertising `Esp32Config.DEVICE_NAME`): verify auto-connect (SC-001), failure + Retry recovery (SC-003/SC-004), and the permission-denied path (FR-003). Requires physical hardware (emulators have no Bluetooth radio).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories.
- **User Story 1 (Phase 3)**: Depends on Foundational.
- **User Story 2 (Phase 4)**: Depends on Foundational; its MainActivity task (T013) depends on US1's T010 (same file).
- **User Story 3 (Phase 5)**: Depends on Foundational and extends US1/US2 code (ViewModel, tests, MainActivity, real connection).
- **Polish (Phase 6)**: Depends on all desired stories being complete.

### Task-level dependencies (same-file / build-on)

- ConnectionViewModel: T008 → T016. ConnectionViewModelTest: T007 → T014.
- BluetoothEsp32Connection: T009 → T017. ConnectionStatusBarTest: T011 → T015.
- MainActivity: T010 → T013 → T018 (all edit the same file — strictly sequential).
- T006 (fake) depends on T004; T008 depends on T003+T004; T012 depends on T003.

### Within Each User Story

- Tests are written before/alongside implementation and pass at the checkpoint.
- Model/seam before ViewModel; ViewModel + real connection before MainActivity wiring; stateless UI before its host wiring.

## Parallel Opportunities

- **Foundational**: T003, T004, T005 are different files with no interdependencies → run in parallel.
  (T006 waits on T004.)
- Across stories after Foundational: the **unit-test/ViewModel track** (T007→T008, later T014→T016)
  and the **UI track** (T011→T012) touch different files and can progress in parallel until they
  meet at MainActivity.
- **T019 [P]** runs after all implementation is complete.

### Parallel Example

```bash
# Foundational model/seam/config in parallel:
Task: "T003 ConnectionState + FailureReason (model/ConnectionState.kt)"
Task: "T004 Esp32Connection interface + exceptions (model/Esp32Connection.kt)"
Task: "T005 Esp32Config constants (data/Esp32Config.kt)"
```

## Implementation Strategy

### MVP First (US1 + US2, both P1)

1. Phase 1 Setup (T001–T002).
2. Phase 2 Foundational (T003–T006).
3. Phase 3 US1 (T007–T010) → ViewModel reaches `Connected`.
4. Phase 4 US2 (T011–T013) → status is visible.
5. **STOP and VALIDATE**: on a real phone with the ESP32, launch → "Connecting…" → "Connected".
   This is the demoable MVP.

### Incremental Delivery

1. Setup + Foundational → shared core ready.
2. US1 → auto-connect logic (unit-tested).
3. US2 → observable status → demo MVP.
4. US3 → failures, timeout, retry, drop handling → robust, feature-complete.
5. Polish → full suite green + on-device smoke check.

## Notes

- [P] = different files, no dependencies on incomplete tasks.
- Every task lists an exact file path.
- Keep it minimal per the constitution: Classic SPP, single bonded device, connection-only. Do NOT
  add command-sending over the link here — that is the next feature and attaches to this seam.
- The automated suite runs with the fake and needs no ESP32; only T020 needs real hardware.
- Commit after each task or logical group.
