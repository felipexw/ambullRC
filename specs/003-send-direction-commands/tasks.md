---
description: "Task list for Send Direction Commands to ESP32"
---

# Tasks: Send Direction Commands to ESP32

**Input**: Design documents from `/specs/003-send-direction-commands/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/command-contract.md, quickstart.md

**Tests**: REQUIRED for this project. Per constitution Principle V, `ControlViewModel`'s
command-decision logic must have unit tests against a fake `Esp32Connection`, so test tasks are
included and are not optional.

**Organization**: Tasks are grouped by user story. US1 (tap sends a command) is Priority P1 and is
the MVP; US2 (disconnected tap is a safe no-op) is Priority P2.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1, US2)
- All paths are repo-relative

## Path Conventions

Single Android module `:app`:

- Main code: `app/src/main/java/com/example/ambullrc/`
- Unit tests (JVM): `app/src/test/java/com/example/ambullrc/`
- Instrumented tests: `app/src/androidTest/java/com/example/ambullrc/`

No new setup tasks are required — this feature reuses the dependencies (`kotlinx-coroutines-android`,
`androidx.lifecycle:lifecycle-viewmodel-ktx`, `kotlinx-coroutines-test`) added by feature 002
(plan.md Technical Context). There is no Phase 1 Setup; task numbering starts at the Foundational
phase.

---

## Phase 1: Foundational (Blocking Prerequisites)

**Purpose**: Extend the `Esp32Connection` seam (real + fake) with the `send` operation that BOTH
user stories depend on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T001 [P] Add `suspend fun send(message: String): Boolean` to the `Esp32Connection` interface in `app/src/main/java/com/example/ambullrc/model/Esp32Connection.kt`, per contracts/command-contract.md (never throws; `true` on success, `false` if not connected or the write fails).
- [X] T002 [P] Extend `FakeEsp32Connection` in `app/src/test/java/com/example/ambullrc/FakeEsp32Connection.kt`: add a read-only `isConnected: Boolean` (true after a successful `connect()`, false after `disconnect()`/`simulateDrop()`), a `sendShouldSucceed: Boolean = true` knob, an ordered `sentCommands: List<String>` recorder, and implement `send(message)` — appends to `sentCommands` and returns `sendShouldSucceed` only when `isConnected`, otherwise returns `false` without recording (depends on T001).
- [X] T003 [P] Extend `BluetoothEsp32Connection` in `app/src/main/java/com/example/ambullrc/data/BluetoothEsp32Connection.kt`: capture `outputStream` alongside the existing `inputStream` in `connect()`; implement `send(message)` by UTF-8-encoding and writing + flushing to `outputStream`, returning `false` if `outputStream` is null or an `IOException` is thrown; clear `outputStream` in `disconnect()` (depends on T001).

**Checkpoint**: Seam + fake + real implementation compile with `send` — user stories can proceed.

---

## Phase 2: User Story 1 - Tapping a direction button commands the vehicle (Priority: P1) 🎯 MVP

**Goal**: Every tap on a direction button sends a distinct, matching command to the ESP32 over the
existing Bluetooth connection.

**Independent Test**: With `ControlViewModel` wired to a connected `FakeEsp32Connection`, tap each
of the four directions and confirm one distinct, matching message is recorded per tap.

### Tests for User Story 1 ⚠️

- [X] T004 [P] [US1] Rewrite `ControlViewModelTest` in `app/src/test/java/com/example/ambullrc/ControlViewModelTest.kt` to construct `ControlViewModel` with a connected `FakeEsp32Connection` (plus the existing `RecordingLogger`) using `kotlinx-coroutines-test` (`Dispatchers.setMain` + `runTest`, injecting a `StandardTestDispatcher` as `ioDispatcher`): assert each of the four directions produces its own matching message (`"UP\n"`, `"DOWN\n"`, `"LEFT\n"`, `"RIGHT\n"` — FR-001, FR-002), a single tap records exactly one message (FR-003), repeated/mixed taps record one message per tap in order (FR-003), the message contains only the direction identity (FR-004), and the existing `DirectionLogger.log` behavior from feature 001 still fires once per tap unchanged (depends on T001, T002).

### Implementation for User Story 1

- [X] T005 [US1] Convert `ControlViewModel` in `app/src/main/java/com/example/ambullrc/viewmodel/ControlViewModel.kt` to an AndroidX `ViewModel`: constructor `(connection: Esp32Connection, logger: DirectionLogger = AndroidDirectionLogger(), ioDispatcher: CoroutineDispatcher = Dispatchers.IO)`; `onDirectionTapped(direction)` keeps the existing `logger.log(direction)` call and additionally launches `viewModelScope.launch { withContext(ioDispatcher) { connection.send("${direction.name}\n") } }`, per contracts/command-contract.md (depends on T001).
- [X] T006 [US1] Update `ControlScreen` in `app/src/main/java/com/example/ambullrc/ui/ControlScreen.kt`: remove the default `viewModel: ControlViewModel = remember { ControlViewModel() }` value — `viewModel` becomes a required parameter, since `ControlViewModel` no longer has a no-arg constructor (depends on T005).
- [X] T007 [US1] Update `MainActivity` in `app/src/main/java/com/example/ambullrc/MainActivity.kt`: construct exactly one `BluetoothEsp32Connection` instance (e.g. a `by lazy` property) and provide it to **both** `ConnectionViewModel`'s existing `viewModelFactory` and a new `ControlViewModel` `by viewModels { viewModelFactory { initializer { ControlViewModel(esp32Connection) } } }`; pass the resulting instance into `ControlScreen(viewModel = controlViewModel)` (depends on T003, T005, T006).
- [X] T008 [US1] Update `ControlScreenTest` in `app/src/androidTest/java/com/example/ambullrc/ControlScreenTest.kt`: change `setContentWith`/each test to construct `ControlViewModel(connection = FakeEsp32Connection().apply { /* connect a fake, or leave disconnected per test */ }, logger = ...)` instead of the old no-arg `ControlViewModel(logger)`, keeping all existing tap-logging assertions passing unchanged (depends on T002, T005, T006).

**Checkpoint**: Tapping each button while connected sends the matching command; T004 and the updated T008 pass. This is the demoable MVP.

---

## Phase 3: User Story 2 - Tapping while not connected does not crash or hang (Priority: P2)

**Goal**: A tap made while the app is not connected to the ESP32 is silently dropped — no crash,
no message sent, no queueing.

**Independent Test**: With `ControlViewModel` wired to a `FakeEsp32Connection` that has never
connected (or has been disconnected), tap a direction button and confirm no message is recorded and
no exception propagates.

### Tests for User Story 2 ⚠️

- [X] T009 [US2] Extend `ControlViewModelTest` in `app/src/test/java/com/example/ambullrc/ControlViewModelTest.kt`: with a `FakeEsp32Connection` that has not had `connect()` called (or has had `simulateDrop()`/`disconnect()` invoked), call `onDirectionTapped` for a direction and assert `sentCommands` stays empty and no exception is thrown (FR-005, FR-006); then connect the same fake and confirm a subsequent tap sends normally with no replay of the earlier missed tap (depends on T004, T002).

### Implementation for User Story 2

No new production code is required for this story: `Esp32Connection.send`'s non-throwing,
`false`-on-not-connected contract (T001/T002/T003, Foundational) already guarantees the required
behavior — `ControlViewModel` (T005) calls `send` unconditionally and never inspects the result.
This phase only adds the test coverage above that proves the guarantee holds end-to-end.

**Checkpoint**: T009 passes without any implementation changes. Both user stories are now covered.

---

## Phase 4: Polish & Validation

**Purpose**: Run the full suite and the on-device smoke check from quickstart.md (Definition of Done).

- [X] T010 [P] Run `./gradlew :app:testDebugUnitTest` and `./gradlew :app:connectedDebugAndroidTest`; confirm all unit and instrumented tests pass (quickstart.md Definition of Done).
- [ ] T011 On-device smoke check per quickstart.md using a real phone connected to a bonded ESP32 running a sketch that echoes received serial lines: verify each direction tap produces a distinct, correctly-identified line (SC-001/SC-002), rapid taps each arrive with no missed/merged commands (SC-004), and tapping while disconnected does not crash the app (SC-003). Requires physical hardware (emulators have no Bluetooth radio) — coordinate with feature 002's still-open on-device task (specs/002-esp32-bluetooth-connection/tasks.md T020).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 1)**: No dependencies — start immediately. BLOCKS both user stories.
- **User Story 1 (Phase 2)**: Depends on Foundational.
- **User Story 2 (Phase 3)**: Depends on Foundational and on US1's T004/T005 (extends the same test file and relies on the same `ControlViewModel` implementation) — not independently implementable before US1, but independently *testable/demoable* once both are done.
- **Polish (Phase 4)**: Depends on both user stories being complete.

### Task-level dependencies (same-file / build-on)

- `Esp32Connection.kt`: T001 → (T002, T003 both depend on T001).
- `ControlViewModel.kt`: T005 depends on T001; T005 is in turn depended on by T007, T008, T009.
- `ControlViewModelTest.kt`: T004 → T009 (same file, sequential).
- `MainActivity.kt`: T007 depends on T003, T005, T006 (single edit, sequential with feature 002's existing wiring).
- `ControlScreen.kt`: T006 depends on T005; `ControlScreenTest.kt` T008 depends on T002, T005, T006.

### Within Each User Story

- Tests are written before/alongside implementation and pass at the checkpoint.
- Seam extension (Foundational) before `ControlViewModel`; `ControlViewModel` before the UI/Activity wiring that constructs it.

## Parallel Opportunities

- **Foundational**: T001 must land first; T002 and T003 then touch different files and can run in parallel.
- T004 (test) and T005 (implementation) can be drafted in parallel once T001/T002 are done, but T004 will only pass once T005 exists.
- **T010 [P]** runs after all implementation is complete.

### Parallel Example

```bash
# Foundational: T001 first, then in parallel:
Task: "T002 Extend FakeEsp32Connection with send()/isConnected/sentCommands (test/FakeEsp32Connection.kt)"
Task: "T003 Extend BluetoothEsp32Connection with outputStream + send() (data/BluetoothEsp32Connection.kt)"
```

## Implementation Strategy

### MVP First (US1 only)

1. Phase 1 Foundational (T001–T003).
2. Phase 2 US1 (T004–T008) → tapping while connected sends the correct command.
3. **STOP and VALIDATE**: on a real phone with the ESP32, tap each direction and confirm the
   matching line arrives. This is the demoable MVP.

### Incremental Delivery

1. Foundational → seam extended, both real and fake support `send`.
2. US1 → commands are sent and reach the ESP32 (unit + instrumented tests green) → demo MVP.
3. US2 → disconnected taps proven safe (test-only addition, no new production code).
4. Polish → full suite green + on-device smoke check.

## Notes

- [P] = different files, no dependencies on incomplete tasks.
- Every task lists an exact file path.
- Keep it minimal per the constitution: no acknowledgment/response handling, no queueing, no new
  commands beyond the four existing directions.
- The automated suite runs entirely against `FakeEsp32Connection` and needs no ESP32; only T011
  needs real hardware.
- Commit after each task or logical group.
