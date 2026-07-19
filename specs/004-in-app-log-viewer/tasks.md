---
description: "Task list for In-App Log Viewer Widget"
---

# Tasks: In-App Log Viewer Widget

**Input**: Design documents from `/specs/004-in-app-log-viewer/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md,
contracts/debug-log-contract.md, quickstart.md

**Tests**: REQUIRED for this project. Per constitution Principle V, `DebugLog`'s buffer logic and
both ViewModels' new logging behavior must have unit tests, and the on-screen widget must have a
Compose UI test — so test tasks are included and are not optional.

**Organization**: Tasks are grouped by user story. US1 (see live diagnostic messages below the
control arrows) is Priority P1 and is the MVP; US2 (review the ordered history, not just the latest
message) is Priority P2.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1, US2)
- All paths are repo-relative

## Path Conventions

Single Android module `:app`:

- Main code: `app/src/main/java/com/example/ambullrc/`
- Unit tests (JVM): `app/src/test/java/com/example/ambullrc/`
- Instrumented tests: `app/src/androidTest/java/com/example/ambullrc/`

No new dependencies are required — this feature reuses Jetpack Compose, AndroidX ViewModel, and
kotlinx-coroutines (`StateFlow`) already present since features 001–003 (plan.md Technical Context).
There is no Phase 1 Setup; task numbering starts at the Foundational phase.

---

## Phase 1: Foundational (Blocking Prerequisites)

**Purpose**: Create the `DebugLog` ring buffer both user stories depend on (US1 needs it to hold and
expose messages at all; US2's ordering/history guarantee is provided natively by this same buffer).

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T001 Create `DebugLog` in `app/src/main/java/com/example/ambullrc/viewmodel/DebugLog.kt`: `val entries: StateFlow<List<String>>` (oldest first) and `fun add(message: String)` that appends then truncates to the most recent 50 entries (`(current + message).takeLast(50)`), per contracts/debug-log-contract.md and data-model.md Decision 2. Never throws, never blocks.
- [X] T002 Create `DebugLogTest` in `app/src/test/java/com/example/ambullrc/DebugLogTest.kt`: assert `entries` starts empty; a single `add` is reflected immediately in order; multiple `add` calls preserve chronological (oldest-first) order; adding 55 entries keeps only the most recent 50, dropping the oldest (FR-007) (depends on T001).

**Checkpoint**: `DebugLog` compiles with passing tests — user stories can proceed.

---

## Phase 2: User Story 1 - See live diagnostic messages below the control arrows (Priority: P1) 🎯 MVP

**Goal**: A small widget directly below the direction arrows shows connection-status changes and
each direction tap's send/drop outcome, updating automatically with the newest entry visible.

**Independent Test**: With `ConnectionViewModel`/`ControlViewModel` wired to a shared `DebugLog` and
a `FakeEsp32Connection`, trigger a connection-state change and a direction tap, and confirm a
matching entry appears in `DebugLog.entries` for each, with the widget rendering and auto-scrolling
to the newest.

### Tests for User Story 1 ⚠️

- [X] T003 [P] [US1] Extend `ControlViewModelTest` in `app/src/test/java/com/example/ambullrc/ControlViewModelTest.kt`: construct `ControlViewModel` with an injected `DebugLog`; assert a tap while connected appends `"<DIRECTION> -> sent"` and a tap while disconnected appends `"<DIRECTION> -> dropped (not connected)"`, in both cases without disturbing the existing `sentCommands`/logger assertions from feature 003 (depends on T001).
- [X] T004 [P] [US1] Extend `ConnectionViewModelTest` in `app/src/test/java/com/example/ambullrc/ConnectionViewModelTest.kt`: construct `ConnectionViewModel` with an injected `DebugLog`; assert `connect()` success appends `"Connecting to ESP32…"` then `"Connected"` in order, each failure reason appends a `"Connect failed: ..."` entry, and a mid-session drop appends `"Connection lost"` — without disturbing existing `state` assertions (depends on T001).

### Implementation for User Story 1

- [X] T005 [US1] Modify `ControlViewModel` in `app/src/main/java/com/example/ambullrc/viewmodel/ControlViewModel.kt`: add `debugLog: DebugLog = DebugLog()` constructor parameter; after `connection.send(...)` completes in `onDirectionTapped`, call `debugLog.add("${direction.name} -> ${if (sent) "sent" else "dropped (not connected)"}")` (depends on T001).
- [X] T006 [US1] Modify `ConnectionViewModel` in `app/src/main/java/com/example/ambullrc/viewmodel/ConnectionViewModel.kt`: add `debugLog: DebugLog = DebugLog()` constructor parameter; call `debugLog.add(...)` at each `_state` transition (`Connecting` → `"Connecting to ESP32…"`, success → `"Connected"`, each `Failed` case → `"Connect failed: <reason>"`, drop detected in `monitorForDrop` → `"Connection lost"`) (depends on T001).
- [X] T007 [US1] Create `DebugLogPanel` composable in `app/src/main/java/com/example/ambullrc/ui/DebugLogPanel.kt`: stateless, takes `entries: List<String>`; renders a fixed-height, `testTag("debug_log")`-tagged `LazyColumn` of the entries; auto-scrolls to the last item whenever `entries.size` changes, satisfying FR-001/FR-005 and US1 Acceptance Scenario 3 (newest visible with no operator action).
- [X] T008 [US1] Modify `MainActivity` in `app/src/main/java/com/example/ambullrc/MainActivity.kt`: construct one shared `DebugLog` instance; pass it into both the `connectionViewModel` and `controlViewModel` `viewModelFactory` initializers; render `DebugLogPanel(entries = debugLog.entries.collectAsState().value)` directly below the existing `ControlScreen(...)` call inside the root `Column` (depends on T005, T006, T007).
- [X] T009 [US1] Create `DebugLogPanelTest` in `app/src/androidTest/java/com/example/ambullrc/DebugLogPanelTest.kt`: `composeRule.setContent { DebugLogPanel(entries = ...) }`; assert the `debug_log`-tagged node is displayed; assert each provided entry's text is displayed; with more entries than fit in the fixed height, assert the newest (last) entry is displayed without any manual scroll action (US1 Acceptance Scenario 3).

**Checkpoint**: Connection-state changes and tap outcomes appear live in the widget below the
arrows; T003, T004, and T009 pass. This is the demoable MVP.

---

## Phase 3: User Story 2 - Review the ordered sequence of recent events (Priority: P2)

**Goal**: The operator can scroll back through recent entries in chronological order, not just see
the single latest message.

**Independent Test**: Feed several distinct entries into a `DebugLogPanel` in sequence and confirm
they render in chronological order and remain individually readable by scrolling.

### Tests for User Story 2 ⚠️

- [X] T010 [US2] Extend `DebugLogPanelTest` in `app/src/androidTest/java/com/example/ambullrc/DebugLogPanelTest.kt`: feed a sequence of distinct entries exceeding the panel's visible height; assert all appear as separate nodes in oldest-to-newest order, and assert an older entry not currently visible can be revealed and read by scrolling the `debug_log` node (`performScrollToNode`/equivalent) (US2 Acceptance Scenarios 1–2; depends on T007, T009).

### Implementation for User Story 2

No new production code is required for this story: `DebugLog`'s append-ordered, capped buffer
(Foundational, T001) and `DebugLogPanel`'s `LazyColumn` (US1, T007) already provide chronological,
scrollable history for free. This phase only adds the test coverage above that proves the guarantee
holds end-to-end.

**Checkpoint**: T010 passes without any implementation changes. Both user stories are now covered.

---

## Phase 4: Polish & Validation

**Purpose**: Run the full suite and the validation steps from quickstart.md (Definition of Done).

- [X] T011 [P] Run `./gradlew :app:testDebugUnitTest` and `./gradlew :app:connectedDebugAndroidTest`; confirm all unit and instrumented tests pass (quickstart.md Build & automated tests).
- [X] T012 Manual validation per quickstart.md (emulator or device, no ESP32 required): confirm the widget is visible below the arrows, tapping a direction adds a "dropped" entry (no Bluetooth radio on emulator), several taps appear in order with the newest auto-scrolled into view, and entries survive a screen rotation.
- [X] T013 On-device validation per quickstart.md using a physical phone paired with a real ESP32: confirm connection-state messages appear as the app auto-connects, each direction tap shows `"<DIRECTION> -> sent"`, and moving the ESP32 out of range shows `"Connection lost"` followed by `"dropped (not connected)"` entries with no crash. Requires physical hardware — this doubles as a more convenient alternative to feature 003's T011 (`specs/003-send-direction-commands/tasks.md`).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 1)**: No dependencies — start immediately. BLOCKS both user stories.
- **User Story 1 (Phase 2)**: Depends on Foundational.
- **User Story 2 (Phase 3)**: Depends on Foundational and on US1's T007/T009 (extends the same test file and relies on the same `DebugLogPanel` implementation) — not independently implementable before US1, but independently *testable/demoable* once both are done.
- **Polish (Phase 4)**: Depends on both user stories being complete.

### Task-level dependencies (same-file / build-on)

- `DebugLog.kt`: T001 → T002, and → T005, T006 (both ViewModels take it as a parameter).
- `ControlViewModel.kt`: T005 depends on T001; depended on by T003 (test) and T008 (MainActivity wiring).
- `ConnectionViewModel.kt`: T006 depends on T001; depended on by T004 (test) and T008 (MainActivity wiring).
- `DebugLogPanel.kt`: T007 → T008 (MainActivity), T009 (test), T010 (test extension, same file as T009).
- `MainActivity.kt`: T008 depends on T005, T006, T007 (single edit, sequential with existing feature 002/003 wiring).
- `DebugLogPanelTest.kt`: T009 → T010 (same file, sequential).

### Within Each User Story

- Tests are written before/alongside implementation and pass at the checkpoint.
- Foundational buffer (`DebugLog`) before the ViewModels that write to it; ViewModels before the
  `MainActivity` wiring that constructs and shares them.

## Parallel Opportunities

- **Foundational**: T001 must land first; T002 then follows (same file dependency, not parallel).
- **US1 tests**: T003 and T004 touch different files and can run in parallel once T001 is done.
- T007 (`DebugLogPanel`) has no dependency on T005/T006 and can be built in parallel with them.
- **T011 [P]** runs after all implementation is complete.

### Parallel Example

```bash
# US1: once T001 is done, in parallel:
Task: "T003 Extend ControlViewModelTest with DebugLog assertions"
Task: "T004 Extend ConnectionViewModelTest with DebugLog assertions"
Task: "T007 Create DebugLogPanel composable"
```

## Implementation Strategy

### MVP First (US1 only)

1. Phase 1 Foundational (T001–T002).
2. Phase 2 US1 (T003–T009) → widget shows live connection/tap diagnostics below the arrows.
3. **STOP and VALIDATE**: run the emulator manual check from quickstart.md. This is the demoable MVP.

### Incremental Delivery

1. Foundational → `DebugLog` buffer ready, unit-tested.
2. US1 → live widget wired end-to-end (unit + instrumented tests green) → demo MVP.
3. US2 → ordered/scrollable history proven (test-only addition, no new production code).
4. Polish → full suite green + manual + on-device validation.

## Notes

- [P] = different files, no dependencies on incomplete tasks.
- Every task lists an exact file path.
- Keep it minimal per the constitution: no real Logcat/stdout capture, no clear/copy/export action,
  no persistence — see research.md Decision 1 for why.
- The automated suite runs entirely against fakes/Compose test rules and needs no ESP32; only T013
  needs real hardware.
- Commit after each task or logical group.
