---
description: "Task list for Horn Sound Command (Played by the ESP32)"
---

# Tasks: Horn Sound Command (Played by the ESP32)

**Input**: Design documents from `/specs/008-horn-sound-command/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md,
contracts/horn-contract.md, quickstart.md

**Tests**: REQUIRED for this project. Per constitution Principle V, `ControlViewModel`'s new
cooldown/gating logic must have unit tests against the existing `FakeEsp32Connection`, so test
tasks are included and are not optional.

**Organization**: Tasks are grouped by user story. US1 (sound the horn without sacrificing
control) and US2 (a repeat tap within 1500ms does nothing) are both Priority P1 — US1 is the MVP
and its implementation already satisfies US2's guarantee, so US2 only adds explicit test coverage
isolating that scenario. US3 (clear Unavailable/Ready/Sounding feedback) is Priority P3 and
likewise adds only test coverage on top of US1's implementation.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- All paths are repo-relative

## Path Conventions

Single Android module `:app`:

- Main code: `app/src/main/java/com/example/ambullrc/`
- Unit tests (JVM): `app/src/test/java/com/example/ambullrc/`
- Instrumented tests: `app/src/androidTest/java/com/example/ambullrc/`

No Setup or Foundational phase is needed for this feature: it adds no new dependency (the
`Campaign` icon is already present via feature 007's `material-icons-extended`) and makes no
`Esp32Connection`/`BluetoothEsp32Connection`/`FakeEsp32Connection` changes at all (research.md) —
everything lives in `ControlViewModel` and `ControlScreen`.

---

## Phase 1: User Story 1 - Sound the horn without sacrificing control (Priority: P1) 🎯 MVP

**Goal**: A tap sends exactly one `"HORN\n"` trigger; the control shows "sounding" for a fixed
1500ms and then automatically re-enables, with zero dependency on any ESP32 response, and without
ever affecting the directional-repeat loop's timing.

**Independent Test**: With `ControlViewModel` wired to a connected `FakeEsp32Connection`, call
`setConnected(true)`, tap the horn, confirm `"HORN\n"` was sent and `hornPlaying`/`hornAvailable`
flip accordingly; advance virtual time by the cooldown duration and confirm they flip back with no
fake response needed; hold a direction at the same time and confirm its repeat cadence is
unaffected throughout.

### Tests for User Story 1 ⚠️

- [X] T001 [P] [US1] Add horn coverage to `ControlViewModelTest` in
  `app/src/test/java/com/example/ambullrc/ControlViewModelTest.kt`, using
  `kotlinx-coroutines-test` virtual time (`advanceTimeBy`/`runCurrent`, as the existing directional
  and lights tests already do): while connected, `onHornTapped()` sends exactly one `"HORN\n"` and
  immediately `hornPlaying == true` / `hornAvailable == false` (FR-002, FR-003); advancing virtual
  time by exactly `hornCooldownMillis` flips both back (`hornPlaying == false`, `hornAvailable ==
  true`) with **no** value ever pushed on the fake — advancing by less does not (FR-005); calling
  `setConnected(false)` mid-cooldown immediately resets `hornPlaying`/`hornAvailable` to
  `false`/`false`, and a subsequent `setConnected(true)` makes the control available right away
  rather than waiting out the original window (FR-008, research.md's stale-job fix); holding a
  direction (via `onDirectionPressed`) while tapping the horn produces both the repeating
  directional sends (unchanged 100ms cadence) and the horn trigger, uninterrupted (FR-007); every
  `"HORN\n"` send outcome is also recorded in `debugLog`, matching the existing pattern for
  direction/lights commands.

### Implementation for User Story 1

- [X] T002 [US1] Add horn members to `ControlViewModel` in
  `app/src/main/java/com/example/ambullrc/viewmodel/ControlViewModel.kt`, per
  contracts/horn-contract.md: a `private const val HORN_COOLDOWN_MS = 1500L` (top of file, next to
  `REPEAT_INTERVAL_MS`) and a `hornCooldownMillis: Long = HORN_COOLDOWN_MS` constructor parameter;
  private `_hornPlaying = MutableStateFlow(false)` / `_hornAvailable = MutableStateFlow(false)`
  with public read-only `hornPlaying`/`hornAvailable` `StateFlow` properties; a private `var
  hornResetJob: Job? = null`; `onHornTapped()` that, only while `_hornAvailable.value == true`,
  sends `"HORN\n"` via the existing `withContext(ioDispatcher) { connection.send(...) }` path, and
  on success sets `_hornPlaying.value = true`, recomputes `_hornAvailable`, cancels any existing
  `hornResetJob`, and launches a new one (`viewModelScope.launch { delay(hornCooldownMillis);
  _hornPlaying.value = false; <recompute hornAvailable> }`), recording the outcome in `debugLog`
  the same way direction/lights sends already do; extend the existing `setConnected(connected:
  Boolean)` so that, in addition to its current `_lightsEnabled.value = connected` line, it also
  tracks the `connected` value for horn-availability purposes and, when `connected == false`,
  cancels `hornResetJob` and immediately sets `_hornPlaying.value = false` before recomputing
  `_hornAvailable` (per contracts/horn-contract.md and research.md's stale-job fix).
- [X] T003 [US1] Add a horn button to `ControlScreen` in
  `app/src/main/java/com/example/ambullrc/ui/ControlScreen.kt`: replace the blank
  `Box(Modifier.size(cellSize))` at row 3 (bottom row), column 2 (directly below `RIGHT`, directly
  right of `DOWN`) with a new `HornButton` — `testTag = "btn_horn"`, content description `"Horn"`,
  icon always `Icons.Filled.Campaign`, `enabled = viewModel.hornAvailable.collectAsState().value`,
  `onClick = viewModel::onHornTapped` (a plain tap, not press/release); tint is `OnSurfaceVariant`
  when `hornPlaying == false` and `Accent` when `hornPlaying == true` (matching
  `DirectionButton`'s idle/pressed tints); reuse the existing dimmed `alpha` treatment for the
  disabled (`hornAvailable == false`) case, same as the D-pad and lights button (depends on T002).

### Instrumented test for User Story 1

- [X] T004 [US1] Extend `ControlScreenTest` in
  `app/src/androidTest/java/com/example/ambullrc/ControlScreenTest.kt`: assert the horn button
  renders at its fixed position with `testTag("btn_horn")` between `btn_right` and `btn_down`;
  tapping it while connected sends `"HORN\n"` (via the test's `FakeEsp32Connection`) and the
  button's tint changes to the "sounding" appearance; asserts the button returns to its normal
  appearance and is tappable again after the cooldown elapses (depends on T002, T003).

**Checkpoint**: Tapping the horn button while connected sends the trigger and the control
correctly shows/clears "sounding" on a fixed timer with no ESP32 involvement; T001 and T004 pass.
This is the demoable MVP.

---

## Phase 2: User Story 2 - A repeat tap within 1500ms of the last one does nothing (Priority: P1)

**Goal**: Explicit, isolated proof that any number of taps within an active cooldown window
produces exactly one `"HORN\n"` send, never more.

**Independent Test**: Tap the horn control, then tap it several more times in immediate
succession (well within the cooldown); confirm only the first tap's `"HORN\n"` reaches
`sentCommands`; advance virtual time past the cooldown and tap again; confirm a second `"HORN\n"`
is sent.

### Tests for User Story 2 ⚠️

- [X] T005 [US2] Add a dedicated repeat-tap test to `ControlViewModelTest` in
  `app/src/test/java/com/example/ambullrc/ControlViewModelTest.kt`: while connected, call
  `onHornTapped()` three or more times in immediate succession (no time advanced between calls)
  and assert `FakeEsp32Connection.sentCommands` contains exactly one `"HORN\n"`; then advance
  virtual time past `hornCooldownMillis` and call `onHornTapped()` again, asserting a second
  `"HORN\n"` is now present (SC-004) (depends on T002).

### Implementation for User Story 2

No new production code is required for this story: `onHornTapped()`'s `hornAvailable` gate and the
cooldown timer (T002, Phase 1) already guarantee the required behavior. This phase only adds the
test coverage above that proves the guarantee holds for repeat taps specifically, not just a
single tap-then-wait cycle.

**Checkpoint**: T005 passes without any further implementation changes. Both P1 user stories are
now covered.

---

## Phase 3: User Story 3 - Clear feedback on whether the horn can be used (Priority: P3)

**Goal**: Explicit, isolated proof that Unavailable, Ready, and Sounding are all visually and
behaviorally distinct.

**Independent Test**: Render the control screen disconnected, connected-idle, and
connected-mid-cooldown; confirm each of the three states is distinguishable and that tapping while
Unavailable or Sounding has no effect.

### Tests for User Story 3 ⚠️

- [X] T006 [US3] Add a three-states test to `ControlScreenTest` in
  `app/src/androidTest/java/com/example/ambullrc/ControlScreenTest.kt`: with `connected = false`,
  assert the horn button is disabled with the same dimmed appearance the D-pad/lights buttons use
  when unavailable, and that a tap on it produces no `"HORN\n"`; with `connected = true` and no
  prior tap, assert it is enabled with its normal (non-`Accent`) tint; after tapping it, assert it
  is disabled with the `Accent`-tinted "sounding" appearance, distinct from the disconnected
  (Unavailable) appearance (depends on T002, T003, T004).

### Implementation for User Story 3

No new production code is required for this story: `ControlScreen`'s tint/enabled logic (T003)
already implements all three states. This phase only adds the test coverage above that explicitly
asserts they are distinct from one another, not just individually correct.

**Checkpoint**: T006 passes without any further implementation changes. All three user stories are
now covered.

---

## Phase 4: Polish & Validation

**Purpose**: Run the full suite and the on-device smoke check from quickstart.md (Definition of
Done).

- [X] T007 [P] Run `./gradlew :app:testDebugUnitTest` and `./gradlew :app:connectedDebugAndroidTest`;
  confirm all unit and instrumented tests pass (quickstart.md Definition of Done).
- [ ] T008 On-device smoke check per quickstart.md, using a real phone connected to a bonded ESP32
  running a sketch that plays a sound on receiving a `HORN` line (no response required — see
  spec.md Clarifications): verify a tap sounds the real horn and the button re-enables itself
  after about 1.5 seconds on its own (SC-001, SC-005); verify holding a direction and tapping the
  horn produces both, uninterrupted (SC-002, SC-003); verify rapid repeat taps mid-cooldown only
  ever send one trigger (SC-004); verify disconnecting mid-cooldown and reconnecting leaves the
  control immediately available rather than stuck waiting (FR-008).

---

## Dependencies & Execution Order

### Phase Dependencies

- **User Story 1 (Phase 1)**: No dependencies — start immediately. This is the MVP.
- **User Story 2 (Phase 2)**: Depends on US1's T002 (extends the same `ControlViewModel`
  implementation and the same `ControlViewModelTest` file) — not independently implementable
  before US1, but independently testable/demoable once both are done.
- **User Story 3 (Phase 3)**: Depends on US1's T002/T003/T004 (extends the same `ControlScreen`
  implementation and `ControlScreenTest` file) — same relationship as US2.
- **Polish (Phase 4)**: Depends on all three user stories being complete.

### Task-level dependencies (same-file / build-on)

- `ControlViewModel.kt`: T002 has no dependencies; T005 (Phase 2) depends on it.
- `ControlViewModelTest.kt`: T001 → T005 (same file, sequential; T005 adds a case, doesn't modify
  T001's).
- `ControlScreen.kt`: T003 depends on T002.
- `ControlScreenTest.kt`: T004 depends on T002, T003; T006 (Phase 3) depends on T004 (same file,
  sequential).

### Within Each User Story

- Tests are written before/alongside implementation and pass at the checkpoint.
- `ControlViewModel` changes before the UI changes that read them.

## Parallel Opportunities

- T001 (unit test) and T003/T004 (UI) touch different files from each other and could be drafted
  in parallel, though T001 will only pass once T002 exists.
- **T007 [P]** runs after all implementation is complete.

### Parallel Example

```bash
# Phase 1: T001 and T002 can be drafted in parallel (different files); T003 then depends on T002.
Task: "T001 Add horn cooldown/gating/concurrency tests (test/ControlViewModelTest.kt)"
Task: "T002 Add horn members to ControlViewModel (viewmodel/ControlViewModel.kt)"
```

## Implementation Strategy

### MVP First (US1 only)

1. Phase 1 US1 (T001–T004) → tap sounds the horn, cooldown shown correctly, directions unaffected.
2. **STOP and VALIDATE**: on a real phone with an ESP32 that plays a sound on `HORN`, tap the
   button and confirm the real horn sounds and the button re-enables itself after ~1.5s. This is
   the demoable MVP.

### Incremental Delivery

1. US1 → core trigger + cooldown + concurrency proven (unit + instrumented tests green) → demo MVP.
2. US2 → repeat-tap gating explicitly proven (test-only addition, no new production code).
3. US3 → three-state visual distinctness explicitly proven (test-only addition, no new production
   code).
4. Polish → full suite green + on-device smoke check.

## Notes

- [P] = different files, no dependencies on incomplete tasks.
- Every task lists an exact file path.
- Keep it minimal per the constitution: one new outbound message (`"HORN\n"`), zero new inbound
  messages, zero `Esp32Connection` changes, zero new dependencies.
- The automated suite runs entirely against the existing `FakeEsp32Connection` and needs no ESP32;
  only T008 needs real hardware.
- Commit after each task or logical group.
