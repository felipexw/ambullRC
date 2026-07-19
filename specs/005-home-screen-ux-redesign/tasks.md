---

description: "Task list for feature implementation"
---

# Tasks: Home Screen UX Redesign

**Input**: Design documents from `/specs/005-home-screen-ux-redesign/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ui-contract.md, quickstart.md

**Tests**: Included — Constitution Principle V mandates unit + integration test coverage for every
feature; this is not optional for this project.

**Organization**: Tasks are grouped by user story (US1 = header, US2 = controls, US3 = log panel)
to enable independent implementation and testing of each story, per plan.md's file-level split.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- File paths are relative to the repository root

## Path Conventions

Single Android module: `app/src/main/java/com/example/ambullrc/`, unit tests in
`app/src/test/java/com/example/ambullrc/`, instrumented tests in
`app/src/androidTest/java/com/example/ambullrc/` (existing project layout — see CLAUDE.md).

---

## Phase 1: Setup

**Purpose**: No new project/dependency setup is required — this feature reuses the existing
Gradle module, Compose/Material3 BOM, and test tooling from features 001-004 (plan.md Technical
Context: "no new dependency").

- [X] T001 Confirm the project builds clean before starting: run `./gradlew testDebugUnitTest connectedDebugAndroidTest assembleDebug` from repo root (with `JAVA_HOME` set per CLAUDE.md) and record a passing baseline.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The shared design-token palette all three redesigned areas render with. Must land
before any story's visual implementation task, since every story's Composable references these
tokens (research.md Decision 1, plan.md Constitution Check row II).

**⚠️ CRITICAL**: No user story implementation task can begin until this phase is complete.

- [X] T002 Replace the default Compose-starter colors in `app/src/main/java/com/example/ambullrc/ui/theme/Color.kt` with the design-token palette from spec.md/README (background `#151210`; surfaces `#1c1917`/`#211e1b`/`#2a2622`; on-surface `#ece1d9`/`#d6c3b7`; outline `#9c8b80`/`#4d443c`; accent `#ff9a5a`; connecting bg/fg/dot `#4d3c00`/`#ffe08a`/`#ffd166`; connected bg/fg/dot `#28401b`/`#c3e8a8`/`#a2d485`; disconnected/error bg/fg/dot `#93000a`/`#ffdad6`/`#ffb4ab`).
- [X] T003 Rewrite `app/src/main/java/com/example/ambullrc/ui/theme/Theme.kt` so `AmbullRCTheme` always builds a fixed `darkColorScheme(...)` from the T002 tokens — remove the `darkTheme`/`dynamicColor` parameters and the light-scheme/dynamic-color branches entirely (research.md Decision 1, contracts/ui-contract.md `AmbullRCTheme` section).
- [X] T004 Update the `AmbullRCTheme { ... }` call site in `app/src/main/java/com/example/ambullrc/MainActivity.kt` to match T003's new no-argument signature (drop any `darkTheme`/`dynamicColor` arguments if present).

**Checkpoint**: Theme compiles and renders the fixed dark palette. User story implementation can now begin, in any order (US1/US2/US3 touch disjoint files per plan.md's Project Structure).

---

## Phase 3: User Story 1 - See connection status at a glance (Priority: P1) 🎯 MVP

**Goal**: The header shows connection state as a color-coded, animated status pill with a Retry
action that appears only when disconnected (spec.md FR-001..004).

**Independent Test**: Drive `ConnectionStatusBar` through `Idle`, `Connecting`, `Connected`, and
`Failed` states in isolation (unit + Compose UI tests) and confirm each renders its own
color/label/dot/animation/Retry-visibility combination, with no dependency on US2 or US3.

### Tests for User Story 1

- [X] T005 [P] [US1] Rewrite `app/src/androidTest/java/com/example/ambullrc/ConnectionStatusBarTest.kt` to assert, per data-model.md's `ConnectionState` → presentation mapping: `Connecting` shows the amber pill with an animated dot and no Retry; `Connected` shows the green pill, steady dot, no Retry, and the device name; `Idle` and `Failed` both show the red/"Disconnected" pill with Retry visible; tapping Retry still invokes `onRetry` (keep the existing `tappingRetryInvokesOnRetry` case, updated to the new tag/text if changed).

### Implementation for User Story 1

- [X] T006 [US1] Redesign `app/src/main/java/com/example/ambullrc/ui/ConnectionStatusBar.kt`: render the device name (bonded name when `Connected`, `"No device"` otherwise, `overflow = TextOverflow.Ellipsis` + `maxLines = 1`) and a status pill built from the data-model.md mapping (background/foreground/dot `Color` per bucket, label per bucket), pulsing the dot via `animateFloatAsState`/`InfiniteTransition` only while `Connecting` (contracts/ui-contract.md `ConnectionStatusBar` section); keep the `(state, onRetry, modifier)` signature and the `btn_retry` test tag, showing Retry only for the `Idle`/`Failed` bucket (data-model.md `showRetry` column).

**Checkpoint**: User Story 1 is fully functional and testable independently — `./gradlew testDebugUnitTest connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.ambullrc.ConnectionStatusBarTest` passes with no changes to US2/US3 files.

---

## Phase 4: User Story 2 - Drive with clear, responsive controls (Priority: P2)

**Goal**: The D-pad shows a cross layout with a distinct pressed-state highlight per button,
dims and ignores input while disconnected, and shows a connection-aware hint line (spec.md
FR-005..008).

**Independent Test**: Render `ControlScreen` with `connected = true`/`false` in isolation (Compose
UI tests) and confirm press/release visuals, disabled-dim appearance, and hint text — no
dependency on US1's pill or US3's log panel (though `connected` is sourced from the same
`ConnectionState` in the running app, per MainActivity wiring in T010).

### Tests for User Story 2

- [X] T007 [P] [US2] Rewrite `app/src/androidTest/java/com/example/ambullrc/ControlScreenTest.kt` to pass a `connected` parameter to `ControlScreen`: keep the existing per-direction press/release and pixel-signature-change assertions for `connected = true`; add cases asserting that with `connected = false` all four buttons (`btn_up`/`btn_down`/`btn_left`/`btn_right`) are dimmed (e.g. assert `alpha`/pixel signature matches the disabled appearance) and a touch-down does not invoke the logger; add a case asserting the hint text node reads the "waiting for connection" copy when `connected = false` and the "hold a direction" copy when `connected = true`.

### Implementation for User Story 2

- [X] T008 [US2] Redesign `app/src/main/java/com/example/ambullrc/ui/ControlScreen.kt`: change the signature to `ControlScreen(viewModel: ControlViewModel, connected: Boolean, modifier: Modifier = Modifier)` (contracts/ui-contract.md `ControlScreen` section); lay the four `DirectionButton`s out in a 3×3 cross using nested `Row`/`Box` (research.md Decision 2) — Up at row 1, Left/center-hub/Right at row 2, Down at row 3, each interactive cell a fixed 76dp `Box`, plus a non-interactive decorative center hub `Box` (circle outline per spec.md's `#4d443c` token); when `connected == false`, apply a dimmed alpha to all four buttons and ignore press/release calls to the `ControlViewModel` (FR-007); add a hint `Text` below the grid switching copy based on `connected` (FR-008).
- [X] T009 [US2] Update `DirectionButton`'s pressed/idle visuals in the same file to match the design tokens: idle background `#2a2622` with `#d6c3b7` icon tint; pressed background = accent (`#ff9a5a`) with `#3a1e00` icon tint and a soft accent-colored glow (`Modifier.shadow`/border approximating the design's `0 0 0 4px accent33`), animated via `animateColorAsState` (FR-006).
- [X] T010 [US2] Update the `ControlScreen(...)` call site in `app/src/main/java/com/example/ambullrc/MainActivity.kt` to pass `connected = (state == ConnectionState.Connected)`, derived from the already-collected `connectionViewModel.state`.

**Checkpoint**: User Stories 1 AND 2 both work independently — each area's own instrumented test class passes without requiring US3's changes.

---

## Phase 5: User Story 3 - Inspect activity in a collapsible log panel (Priority: P3)

**Goal**: The log panel becomes a tap-or-drag collapsible sheet showing color-coded, timestamped
entries (spec.md FR-009..015). This is the phase that introduces the `LogEntry` data model change
and therefore also touches the two ViewModels' logging call sites (data-model.md, research.md
Decision 5).

**Independent Test**: Feed `DebugLogPanel` a fixed `List<LogEntry>` in isolation (Compose UI
tests) and confirm collapsed/expanded rendering, tap-to-toggle, drag-to-resize-and-snap, and
per-category/level coloring — plus unit tests confirming `DebugLog`/`ConnectionViewModel`/
`ControlViewModel` produce the right `LogEntry` values, all independent of US1/US2's visuals.

### Tests for User Story 3

- [X] T011 [P] [US3] Rewrite `app/src/test/java/com/example/ambullrc/DebugLogTest.kt` for the new `LogEntry`-based `DebugLog`: `add(category, level, message)` appends a `LogEntry` carrying a `timestamp`, the given `category`/`level`, and `message`; ordering and the 50-entry cap behave exactly as before (existing `startsEmpty`/`multipleAddsPreserveChronologicalOrder`/`cappedAtFiftyEntriesDroppingOldestFirst` cases, adapted to construct/compare `LogEntry` values); a default `level` of `INFO` is used when omitted.
- [X] T012 [P] [US3] Update `app/src/test/java/com/example/ambullrc/ConnectionViewModelTest.kt`'s log-related assertions (if any currently inspect `debugLog.entries.value` as strings) to assert against `LogEntry.category`/`level`/`message` per data-model.md's table (e.g. connect-begin → `CONNECTION`/`INFO`/`"Connecting to ESP32…"`; connect-fail → `CONNECTION`/`ERROR`; connection-lost → `CONNECTION`/`ERROR`; permission-denied → `APP`/`ERROR`).
- [X] T013 [P] [US3] Update `app/src/test/java/com/example/ambullrc/ControlViewModelTest.kt`'s log-related assertions to assert `LogEntry.category == SENT` with `level == INFO` for a successful send and `level == WARN` for a dropped (not-connected) send, per data-model.md's table.
- [X] T014 [P] [US3] Rewrite `app/src/androidTest/java/com/example/ambullrc/DebugLogPanelTest.kt` for the new `List<LogEntry>` signature and collapsible behavior: collapsed state shows only the `"LOGS · <count>"` label and no entry rows; tapping the collapsed strip expands it and reveals entries (timestamp + `category.tag` + message all visible per row); tapping again collapses it; dragging the handle past/short-of the midpoint snaps open/closed per spec.md's Edge Cases (drag < ~10dp treated as tap); the newest entry is scrolled into view when expanded and a new entry arrives; entries with different `category`/`level` render with different node colors (assert via `captureToImage` pixel sampling on the tag/message text, mirroring `ControlScreenTest`'s `pixelSignature()` pattern).

### Implementation for User Story 3

- [X] T015 [P] [US3] Create `app/src/main/java/com/example/ambullrc/viewmodel/LogEntry.kt` with `enum class LogCategory(val tag: String) { SENT("TX"), RECEIVED("RX"), CONNECTION("BLE"), APP("APP") }`, `enum class LogLevel { INFO, WARN, ERROR }`, and `data class LogEntry(val timestamp: LocalTime, val category: LogCategory, val level: LogLevel, val message: String)` (data-model.md `Entity: Log Entry`, contracts/ui-contract.md).
- [X] T016 [US3] Modify `app/src/main/java/com/example/ambullrc/viewmodel/DebugLog.kt` so `entries` is `StateFlow<List<LogEntry>>` and `add(String)` is replaced by `add(category: LogCategory, level: LogLevel = LogLevel.INFO, message: String)`, which builds a `LogEntry` with `LocalTime.now()` and appends/truncates to 50 exactly as before (depends on T015).
- [X] T017 [US3] Update every `debugLog.add(...)` call site in `app/src/main/java/com/example/ambullrc/viewmodel/ConnectionViewModel.kt` to the new signature per data-model.md's table: connecting → `(CONNECTION, INFO, "Connecting to ESP32…")`; connected → `(CONNECTION, INFO, "Connected")`; connect failed → `(CONNECTION, ERROR, "Connect failed: $reason")`; connection lost → `(CONNECTION, ERROR, "Connection lost")`; permission denied → `(APP, ERROR, "Connect failed: PERMISSION_DENIED")` (depends on T016).
- [X] T018 [US3] Update every `debugLog.add(...)` call site in `app/src/main/java/com/example/ambullrc/viewmodel/ControlViewModel.kt` to the new signature: sent → `(SENT, INFO, "${direction.name} -> sent")`; dropped → `(SENT, WARN, "${direction.name} -> dropped (not connected)")` (depends on T016).
- [X] T019 [US3] Redesign `app/src/main/java/com/example/ambullrc/ui/DebugLogPanel.kt` to take `entries: List<LogEntry>` and render as a collapsible sheet (research.md Decisions 3, 4, 6; contracts/ui-contract.md `DebugLogPanel` section): local `remember { mutableStateOf(false) }` for expanded/collapsed and a live drag-height `mutableStateOf<Dp?>(null)`; a drag handle row using `Modifier.pointerInput` + `detectVerticalDragGestures` that both updates live height while dragging and, on release, snaps open/closed based on the midpoint (a drag under ~10dp is treated as a tap toggling the state, per spec.md Edge Cases); height transitions animate via `animateDpAsState` (~200ms); collapsed height fixed at 40dp showing only the handle + `"LOGS · <count>"` label; expanded height fills the space below the header (measured via the parent `Box`, not a hardcoded constant); expanded content shows a "Device Logs" / `"<count> lines"` header row and a `LazyColumn` of entries (timestamp formatted `HH:mm:ss.SSS`, `category.tag` colored per category, message colored per `level`), auto-scrolling to the newest entry on new arrivals (keep the `debug_log` test tag on the scrollable list).
- [X] T020 [US3] Update `app/src/main/java/com/example/ambullrc/MainActivity.kt`'s layout so the redesigned `DebugLogPanel` can overlay the bottom of the screen without being squeezed by the `Column`'s fixed sizing (research.md Decision 4) — wrap `ControlScreen` + `DebugLogPanel` in a `Box` (control screen `fillMaxSize`, log panel `align(Alignment.BottomCenter)`) instead of the current linear `Column` stacking, keeping `ConnectionStatusBar` above that `Box`; pass `debugLog.entries.collectAsState().value` (now `List<LogEntry>`) straight through unchanged.

**Checkpoint**: All three user stories are independently functional and, together, deliver the full redesign described in spec.md.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Whole-feature validation once all three stories are implemented.

- [X] T021 Run `./gradlew testDebugUnitTest connectedDebugAndroidTest assembleDebug` (full suite) and fix any cross-story regression (e.g. a shared token or `MainActivity` wiring mistake introduced by a later story's task).
- [X] T022 Walk through `quickstart.md`'s "Manual validation (emulator or device, no ESP32 required)" section end-to-end on an emulator/device and confirm every numbered step's expected outcome.
- [X] T023 Once a physical phone + paired ESP32 is available, walk through `quickstart.md`'s "On-device validation" section and confirm connection-state color transitions, live press feedback, and log color-coding all match against real hardware (mirrors the still-open on-device tasks tracked in CLAUDE.md's "Current state" section — note the result there when done).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately.
- **Foundational (Phase 2)**: Depends on Setup. BLOCKS all user stories (every story's Composable references the T002/T003 tokens).
- **User Stories (Phase 3-5)**: All depend on Foundational completion. Independent of each other — US1, US2, and US3 touch disjoint sets of files (see plan.md's Project Structure) and can proceed in parallel or in any order.
- **Polish (Phase 6)**: Depends on all three user stories being complete.

### User Story Dependencies

- **User Story 1 (P1)**: `ConnectionStatusBar.kt` + its test only. No dependency on US2/US3.
- **User Story 2 (P2)**: `ControlScreen.kt` + its test + one `MainActivity.kt` wiring line. No dependency on US1/US3 (reads `ConnectionState` directly, not through US1's pill).
- **User Story 3 (P3)**: `LogEntry.kt` (new), `DebugLog.kt`, both ViewModels' log call sites, `DebugLogPanel.kt`, and `MainActivity.kt`'s layout container. No dependency on US1/US2's visual changes — only requires the Phase 2 tokens.

### Within Each User Story

- Tests are written first and should fail against the pre-redesign code, then pass once the paired implementation task lands.
- T015 (LogEntry type) before T016 (DebugLog) before T017/T018 (call sites) before T019 (panel renders `LogEntry`) before T020 (MainActivity layout) — this chain is sequential, not parallel, despite all being [US3].

### Parallel Opportunities

- T005 [US1 test], T007 [US2 test], T011-T014 [US3 tests], and T015 [US3 model] can all be started in parallel once Phase 2 completes — they touch entirely different files.
- Within US3, T011/T012/T013/T014 (four separate test files) are parallel with each other and with T015, but T016 onward is a sequential chain (see above).

---

## Parallel Example: Post-Foundational Kickoff

```bash
# Once T002-T004 (Foundational) are done, these can all start together:
Task: "Rewrite ConnectionStatusBarTest.kt for the new status pill (T005)"
Task: "Rewrite ControlScreenTest.kt for connected/disconnected + cross layout (T007)"
Task: "Rewrite DebugLogTest.kt for LogEntry-based DebugLog (T011)"
Task: "Create LogEntry.kt with LogCategory/LogLevel (T015)"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001).
2. Complete Phase 2: Foundational (T002-T004) — CRITICAL, blocks everything else.
3. Complete Phase 3: User Story 1 (T005-T006).
4. **STOP and VALIDATE**: run `ConnectionStatusBarTest` and manually confirm the pill/Retry
   behavior on an emulator (quickstart.md steps 1-3).
5. Ship if that alone is the desired increment — the D-pad and log panel keep working exactly as
   they do today, just visually unchanged, until US2/US3 land.

### Incremental Delivery

1. Setup + Foundational → shared palette ready.
2. Add User Story 1 → verify independently → the header alone looks redesigned.
3. Add User Story 2 → verify independently → D-pad also redesigned; header still fine.
4. Add User Story 3 → verify independently → full redesign complete, matching spec.md.
5. Phase 6 validates the whole thing together, including the on-device ESP32 pass.

### Parallel Team Strategy

With more than one implementer:

1. Complete Setup + Foundational together first (T001-T004) — small and blocking.
2. Split by story: one person on US1 (T005-T006), one on US2 (T007-T010), one on US3
   (T011-T020, itself a sequential chain within the story).
3. Each story's checkpoint can be validated and merged independently; Phase 6 runs once all three
   land.

---

## Notes

- [P] tasks touch different files with no unmet dependencies at the time they'd run.
- [Story] labels map every user-story-phase task back to spec.md's US1/US2/US3 for traceability.
- This feature changes several existing test files' assertions rather than only adding new tests
  — per plan.md, "this feature's instrumented tests are expected to change alongside the visuals
  they assert on." Confirm old assertions actually fail against pre-redesign code before rewriting
  them, so the test change itself is verified to catch a real regression.
- Commit after each task or logical group, per the project's normal workflow.
- Stop at any checkpoint (end of Phase 3, 4, or 5) to validate that story independently before
  continuing.
