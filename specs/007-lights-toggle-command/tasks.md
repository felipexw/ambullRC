---
description: "Task list for Lights Toggle Command"
---

# Tasks: Lights Toggle Command

**Input**: Design documents from `/specs/007-lights-toggle-command/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md,
contracts/lights-contract.md, quickstart.md

**Tests**: REQUIRED for this project. Per constitution Principle V, `ControlViewModel`'s new
polling/toggle logic must have unit tests against a fake `Esp32Connection`, so test tasks are
included and are not optional.

**Organization**: Tasks are grouped by user story. US1 (toggle the lights, icon reflects the
ESP32's confirmed state) is Priority P1 and is the MVP; US2 (the control reflects connection state)
is Priority P2.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1, US2)
- All paths are repo-relative

## Path Conventions

Single Android module `:app`:

- Main code: `app/src/main/java/com/example/ambullrc/`
- Unit tests (JVM): `app/src/test/java/com/example/ambullrc/`
- Instrumented tests: `app/src/androidTest/java/com/example/ambullrc/`
- Gradle catalog: `gradle/libs.versions.toml`, `app/build.gradle.kts`

---

## Phase 1: Foundational (Blocking Prerequisites)

**Purpose**: Add the one new dependency, and extend the `Esp32Connection` seam (real + both fakes)
with `lightState` — both user stories depend on this.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T001 [P] Add an `androidx-compose-material-icons-extended` alias (group
  `androidx.compose.material`, name `material-icons-extended`) to `gradle/libs.versions.toml`
  next to the existing `androidx-compose-material-icons-core` entry, and add
  `implementation(libs.androidx.compose.material.icons.extended)` to `app/build.gradle.kts` next to
  the existing `material.icons.core` line, per research.md's icon-dependency decision.
- [X] T002 [P] Add `val lightState: StateFlow<Boolean?>` to the `Esp32Connection` interface in
  `app/src/main/java/com/example/ambullrc/model/Esp32Connection.kt`, per
  contracts/lights-contract.md — documented as never set directly by callers, only by the seam's
  own reader loop, and reset to `null` at the start of every `connect()`.
- [X] T003 Extend `BluetoothEsp32Connection` in
  `app/src/main/java/com/example/ambullrc/data/BluetoothEsp32Connection.kt`: add a backing
  `_lightState = MutableStateFlow<Boolean?>(null)` exposed as `lightState`; set
  `_lightState.value = null` as the first step of `connect()`; change `awaitDisconnect()`'s read
  loop to wrap `inputStream` in a `BufferedReader`/`InputStreamReader` and call `readLine()` instead
  of reading raw bytes into a discard buffer — a line exactly equal to `"LIGHT_ON"` sets
  `_lightState.value = true`, `"LIGHT_OFF"` sets it `false`, any other line is ignored; the loop
  still ends (and `awaitDisconnect()` still returns) on `null` (EOF) or `IOException`, unchanged
  from today (depends on T002).
- [X] T004 [P] Extend `FakeEsp32Connection` in
  `app/src/test/java/com/example/ambullrc/FakeEsp32Connection.kt`: add a mutable
  `lightState = MutableStateFlow<Boolean?>(null)` (exposed to `ControlViewModel` as the `StateFlow`
  interface member), reset to `null` inside `connect()`; no other behavior change — tests push
  values directly via `lightState.value = ...` to simulate a recognized inbound line (depends on
  T002).
- [X] T005 [P] Extend `FakeEsp32Connection` in
  `app/src/androidTest/java/com/example/ambullrc/FakeEsp32Connection.kt`: identical addition to
  T004, for the instrumented-test copy of the fake (depends on T002).

**Checkpoint**: Seam + both fakes + real implementation compile with `lightState` — user stories
can proceed.

---

## Phase 2: User Story 1 - Toggle the RC's lights on and off (Priority: P1) 🎯 MVP

**Goal**: A tap sends a toggle command; the lights button's icon only ever shows the ESP32's last
poll-confirmed state, never an optimistic guess, and never interrupts directional commands.

**Independent Test**: With `ControlViewModel` wired to a connected `FakeEsp32Connection`, call
`setConnected(true)`, push a confirmed value onto the fake's `lightState`, tap the lights control,
and confirm the toggle command is sent while `lightsOn` only changes once a further `lightState`
push confirms it.

### Tests for User Story 1 ⚠️

- [X] T006 [P] [US1] Add lights coverage to `ControlViewModelTest` in
  `app/src/test/java/com/example/ambullrc/ControlViewModelTest.kt`, using
  `kotlinx-coroutines-test` virtual time (`advanceTimeBy`/`runCurrent`, as the existing directional
  tests already do): `setConnected(true)` sends `"LIGHTS?\n"` roughly every 500ms and
  `lightsEnabled` stays `false` until `connection.lightState` is pushed non-null, at which point
  `lightsEnabled` becomes `true` and `lightsOn` takes that value (FR-005, FR-007, FR-008);
  `onLightsTapped()` while enabled sends exactly one of `"LIGHTS_ON\n"`/`"LIGHTS_OFF\n"` — the
  opposite of the current `lightsOn` — and `lightsOn` does **not** change until a further
  `lightState` push confirms it (FR-002, FR-003); repeated taps before any confirmation each send
  a command for whatever `lightsOn` still reads at that moment (Edge Cases); holding a direction
  and tapping lights sends both, uninterrupted (FR-006); every lights send outcome is also recorded
  in `debugLog`, matching the existing pattern for direction commands (depends on T002, T004).

### Implementation for User Story 1

- [X] T007 [US1] Add lights members to `ControlViewModel` in
  `app/src/main/java/com/example/ambullrc/viewmodel/ControlViewModel.kt`, per
  contracts/lights-contract.md: a `lightsPollIntervalMillis: Long = 500L` constructor parameter;
  private `_lightsOn = MutableStateFlow(false)` / `_lightsEnabled = MutableStateFlow(false)` with
  public read-only `lightsOn`/`lightsEnabled` `StateFlow` properties; a long-lived
  `viewModelScope.launch { connection.lightState.collect { state -> if (state != null) { _lightsOn.value = state; _lightsEnabled.value = true } } }`
  started once (e.g. in `init`); `setConnected(connected: Boolean)` that starts (on `true`) or
  cancels (on `false`) a repeating `viewModelScope.launch` job sending `"LIGHTS?\n"` via
  `withContext(ioDispatcher) { connection.send(...) }` every `lightsPollIntervalMillis`, resetting
  `_lightsEnabled.value = false` in both directions when the job (re)starts/stops; `onLightsTapped()`
  that, only while `_lightsEnabled.value == true`, sends
  `if (!_lightsOn.value) "LIGHTS_ON\n" else "LIGHTS_OFF\n"` via the same `ioDispatcher`/`send` path
  used elsewhere and records the outcome in `debugLog` the same way direction sends already do
  (depends on T002).
- [X] T008 [US1] Update `ControlScreen` in
  `app/src/main/java/com/example/ambullrc/ui/ControlScreen.kt`: replace the blank
  `Box(Modifier.size(cellSize))` at row 3 (bottom row), column 0 (directly below `LEFT`, directly
  left of `DOWN`) with a new lights button — `testTag = "btn_lights"`, content description
  `"Lights"`, icon `Icons.Filled.Lightbulb` when `viewModel.lightsOn.collectAsState().value` is
  `true` else `Icons.Outlined.Lightbulb`, `enabled = viewModel.lightsEnabled.collectAsState().value`,
  `onClick = viewModel::onLightsTapped`; reuse the existing dimmed/disabled `alpha` treatment the
  `DirectionButton`s already apply, so the enabled/disabled state stays visually distinct from the
  on/off icon per FR-004; also adds `LaunchedEffect(connected) { viewModel.setConnected(connected) }`
  to `ControlScreen`'s own body (absorbing what T009 originally described for `MainActivity` — see
  T009's note) (depends on T007, T001).
- [X] T009 [US1] ~~Update `MainActivity`~~ **Implementation refinement**: the `setConnected`
  forwarding landed in `ControlScreen` instead (see T008) — `ControlScreen` already owns
  forwarding `connected` to the D-pad's enable/disable state, so a `LaunchedEffect(connected) {
  viewModel.setConnected(connected) }` there keeps that responsibility in one place and lets
  `ControlScreenTest` exercise it directly, instead of splitting it across `MainActivity` too.
  `MainActivity` itself needed no change beyond the pre-existing `connected` computation
  (depends on T007).
- [X] T010 [US1] Extend `ControlScreenTest` in
  `app/src/androidTest/java/com/example/ambullrc/ControlScreenTest.kt`: assert the lights button
  renders at its fixed position with `testTag("btn_lights")` between `btn_left` and `btn_down`,
  that its icon swaps between the two `Lightbulb` variants as the `FakeEsp32Connection`'s
  `lightState` is pushed `true`/`false` (via the `ControlViewModel` under test), and that it is
  disabled (matching the existing dimmed-appearance assertion pattern used for the directional
  buttons) while `lightsEnabled` is `false` (depends on T005, T007, T008).

**Checkpoint**: Tapping the lights button while connected sends the right toggle command and the
icon updates only once confirmed by a poll; T006 and the updated T010 pass. This is the demoable
MVP.

---

## Phase 3: User Story 2 - Lights control reflects connection state (Priority: P2)

**Goal**: The lights control is disabled — and stays showing its last-known icon — whenever the
app is disconnected or a fresh confirmation hasn't arrived yet since the last connect.

**Independent Test**: With `ControlViewModel` wired to a `FakeEsp32Connection`, call
`setConnected(false)` (or never call `setConnected(true)`) and confirm `lightsEnabled` is `false`,
no poll commands are sent, and `onLightsTapped()` is a no-op; then `setConnected(true)` again and
confirm the control stays disabled until a fresh `lightState` push arrives.

### Tests for User Story 2 ⚠️

- [X] T011 [US2] Extend `ControlViewModelTest` in
  `app/src/test/java/com/example/ambullrc/ControlViewModelTest.kt`: `setConnected(false)` (after
  having been connected and enabled) stops further `"LIGHTS?\n"` sends and sets `lightsEnabled` to
  `false` while leaving `lightsOn` at its last confirmed value (Edge Cases: last-known state
  persists across a drop); `onLightsTapped()` while `lightsEnabled == false` sends nothing and does
  not change `lightsOn`; reconnecting (`setConnected(true)` again after the fake's `connect()`
  resets `lightState` to `null`, per T004) requires a fresh `lightState` push before `lightsEnabled`
  becomes `true` again — a stale pre-drop value is never reused to re-enable the control early
  (FR-005, FR-008) (depends on T007, T004, T006).

### Implementation for User Story 2

No new production code is required for this story: `setConnected`'s gating and `lightState`'s
reset-on-`connect()` (T003/T004/T007, Foundational + US1) already guarantee the required behavior.
This phase only adds the test coverage above that proves the guarantee holds end-to-end.

**Checkpoint**: T011 passes without any further implementation changes. Both user stories are now
covered.

---

## Phase 4: Polish & Validation

**Purpose**: Run the full suite and the on-device smoke check from quickstart.md (Definition of
Done).

- [X] T012 [P] Run `./gradlew :app:testDebugUnitTest` and `./gradlew :app:connectedDebugAndroidTest`;
  confirm all unit and instrumented tests pass (quickstart.md Definition of Done).
- [ ] T013 On-device smoke check per quickstart.md, using a real phone connected to a bonded ESP32
  running a sketch that implements the `LIGHTS_ON`/`LIGHTS_OFF`/`LIGHTS?` protocol from
  contracts/lights-contract.md: verify a tap toggles the real light and the button's icon updates
  within about one poll interval — not instantly (SC-001); verify holding a direction and tapping
  lights produces both, uninterrupted (SC-003); verify the control disables while disconnected and
  re-enables only after a fresh confirmation on reconnect (SC-004, US2).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 1)**: No dependencies — start immediately. BLOCKS both user stories.
- **User Story 1 (Phase 2)**: Depends on Foundational.
- **User Story 2 (Phase 3)**: Depends on Foundational and on US1's T006/T007 (extends the same test
  file and relies on the same `ControlViewModel` implementation) — not independently implementable
  before US1, but independently *testable/demoable* once both are done.
- **Polish (Phase 4)**: Depends on both user stories being complete.

### Task-level dependencies (same-file / build-on)

- `Esp32Connection.kt`: T002 → T003 depends on it; T004/T005 (the two fakes) also depend on T002.
- `BluetoothEsp32Connection.kt`: T003 depends on T002 only (no other task touches this file).
- `ControlViewModel.kt`: T007 depends on T002; T007 is in turn depended on by T008, T009, T011.
- `ControlViewModelTest.kt`: T006 → T011 (same file, sequential).
- `ControlScreen.kt`: T008 depends on T007 and T001 (needs the new icon dependency); `ControlScreenTest.kt` T010 depends on T005, T007, T008.
- `MainActivity.kt`: T009 depends on T007 (single edit, sequential with the existing wiring).
- `libs.versions.toml` / `app/build.gradle.kts`: T001 has no dependencies, but T008 needs it done first.

### Within Each User Story

- Tests are written before/alongside implementation and pass at the checkpoint.
- Seam extension (Foundational) before `ControlViewModel`; `ControlViewModel` before the UI/Activity
  wiring that reads it.

## Parallel Opportunities

- **Foundational**: T001, T002 have no dependencies and can start immediately in parallel; T003,
  T004, T005 each depend only on T002 and then touch different files, so they too can run in
  parallel once T002 lands.
- T006 (test) and T007 (implementation) can be drafted in parallel once T002/T004 are done, but T006
  will only pass once T007 exists.
- **T012 [P]** runs after all implementation is complete.

### Parallel Example

```bash
# Foundational: T001 and T002 first (no dependencies), then in parallel:
Task: "T003 Extend BluetoothEsp32Connection with lightState + line-parsing read loop (data/BluetoothEsp32Connection.kt)"
Task: "T004 Extend test FakeEsp32Connection with lightState (test/FakeEsp32Connection.kt)"
Task: "T005 Extend androidTest FakeEsp32Connection with lightState (androidTest/FakeEsp32Connection.kt)"
```

## Implementation Strategy

### MVP First (US1 only)

1. Phase 1 Foundational (T001–T005).
2. Phase 2 US1 (T006–T010) → tapping toggles, icon reflects only confirmed state.
3. **STOP and VALIDATE**: on a real phone with the ESP32 running the lights protocol, tap the
   button and confirm the real light and the icon both update correctly. This is the demoable MVP.

### Incremental Delivery

1. Foundational → seam extended, both fakes and the real implementation support `lightState`.
2. US1 → toggling works and the icon reflects confirmed ESP32 state (unit + instrumented tests
   green) → demo MVP.
3. US2 → disconnected/unconfirmed gating proven safe (test-only addition, no new production code).
4. Polish → full suite green + on-device smoke check.

## Notes

- [P] = different files, no dependencies on incomplete tasks.
- Every task lists an exact file path.
- Keep it minimal per the constitution: no request/response correlation, no configurable poll
  interval, no new commands beyond the three listed in contracts/lights-contract.md.
- The automated suite runs entirely against the two `FakeEsp32Connection` copies and needs no ESP32;
  only T013 needs real hardware.
- Commit after each task or logical group.
