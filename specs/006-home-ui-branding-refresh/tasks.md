---

description: "Task list for feature implementation"
---

# Tasks: Home UI & Branding Refresh

**Input**: Design documents from `/specs/006-home-ui-branding-refresh/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ui-contract.md, quickstart.md

**Tests**: Included where the change is behavior-bearing (D-pad sizing, hint removal, header icon)
— Constitution Principle V mandates test coverage for testable logic. The app icon and splash
screen have no Kotlin/Compose surface to assert against (pure Android resource/theme
configuration), so per contracts/ui-contract.md they are verified manually via quickstart.md
instead — this is documented, not skipped.

**Organization**: Tasks are grouped by user story (US1 = D-pad sizing, US2 = hint removal, US3 =
header icon, US4 = app icon + splash screen), ordered by priority (both P1 stories first, then
both P2 stories), per plan.md's file-level split.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4)
- File paths are relative to the repository root

## Path Conventions

Single Android module: `app/src/main/java/com/example/ambullrc/`, resources in
`app/src/main/res/`, instrumented tests in `app/src/androidTest/java/com/example/ambullrc/`
(existing project layout — see CLAUDE.md).

---

## Phase 1: Setup

**Purpose**: No new project/dependency setup is required — this feature reuses the existing
Gradle module, Compose/Material3 BOM, and test tooling from features 001-005 (plan.md Technical
Context: "no new dependency").

- [ ] T001 Confirm the project builds clean before starting: run `./gradlew testDebugUnitTest connectedDebugAndroidTest assembleDebug` from repo root (with `JAVA_HOME` set per CLAUDE.md) and record a passing baseline.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: N/A for this feature. All four stories touch disjoint files (`ControlScreen.kt` for
US1/US2, `ConnectionStatusBar.kt` for US3, `res/drawable*`/`res/values/themes.xml` for US4) and
share no blocking prerequisite — the color tokens they all reuse (`#151210`/`#FF9A5A`) already
exist in `ui/theme/Color.kt` from feature 005 (research.md Decision 3). No tasks in this phase.

**Checkpoint**: Proceed directly to user story phases.

---

## Phase 3: User Story 1 - Controls fill the available space (Priority: P1) 🎯 MVP

**Goal**: The D-pad's four directional buttons and center hub expand to fill the control region's
available width/height instead of a fixed 76dp cell, without overlapping the header or log panel
(spec.md FR-001, FR-002, FR-003).

**Independent Test**: Render `ControlScreen` in isolation and confirm each button's measured
bounds exceed the old fixed 76dp size and stay fully inside the control region — independent of
US2's hint text, US3's header icon, or US4's icon/splash.

### Tests for User Story 1

- [X] T002 [P] [US1] Update `app/src/androidTest/java/com/example/ambullrc/ControlScreenTest.kt`: add assertions that each direction button's (`btn_up`/`btn_down`/`btn_left`/`btn_right`) measured width/height is larger than the old fixed 76dp cell, and that none of the four buttons' or the center hub's bounds fall outside `ControlScreen`'s own root node bounds (contracts/ui-contract.md `ControlScreen` guarantees, spec.md SC-001).

### Implementation for User Story 1

- [X] T003 [US1] Modify `app/src/main/java/com/example/ambullrc/ui/ControlScreen.kt`: remove the fixed `CellSize = 76.dp` constant; wrap the existing 3-row cross layout in a `BoxWithConstraints` inside a `fillMaxSize` `Column`, give each of the three `Row`s `Modifier.weight(1f)`, and give each cell in a row (`DirectionButton`, `CenterHub`, and the blank corner `Box`es) `Modifier.weight(1f)` so the whole grid scales to fill the region while keeping the existing `GridGap` fixed-dp spacing between cells (research.md Decision 4). Keep `DirectionButton`'s and `CenterHub`'s existing pressed/disabled/decorative visual logic unchanged — only their sizing source changes from a constant to the resolved weighted dimension.

**Checkpoint**: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.ambullrc.ControlScreenTest` passes; the D-pad visibly fills the control region on an emulator, with no overlap of the header or log panel (quickstart.md step 5).

---

## Phase 4: User Story 4 - New app icon and matching splash screen (Priority: P1)

**Goal**: The launcher icon shows the supplied orange car/gamepad mark instead of the default
Android-robot placeholder, and a matching dark-background/orange-mark splash screen appears on
cold launch (spec.md FR-006, FR-007, FR-008, FR-009).

**Independent Test**: Install the app and check the launcher/app-drawer icon; force-stop and
cold-launch it and observe the splash screen — independent of US1/US2/US3's Compose changes, since
this story touches only Android resources and the app theme, no Kotlin UI code.

### Implementation for User Story 4

- [X] T004 [P] [US4] Replace the app icon layers: add `specs/006-home-ui-branding-refresh/assets/icon-background-432.png` as `app/src/main/res/drawable-nodpi/ic_launcher_background.png` and `.../icon-foreground-432.png` as `app/src/main/res/drawable-nodpi/ic_launcher_foreground.png`; delete `app/src/main/res/drawable/ic_launcher_background.xml` and `ic_launcher_foreground.xml` (research.md Decision 1, data-model.md `Entity: App Icon`). Leave `app/src/main/res/mipmap-anydpi/ic_launcher.xml` and `ic_launcher_round.xml` untouched — they already reference these two resource names and will resolve to the new PNGs automatically.
- [X] T005 [P] [US4] Add a `splash_background` color (`#151210`) to `app/src/main/res/values/colors.xml`, then add `<item name="android:windowSplashScreenBackground">@color/splash_background</item>` and `<item name="android:windowSplashScreenAnimatedIcon">@drawable/ic_launcher_foreground</item>` to the `Theme.AmbullRC` style in `app/src/main/res/values/themes.xml` (research.md Decision 2, data-model.md `Entity: Splash Screen`). No new dependency, no new theme, no new Activity.
- [X] T006 [US4] Manually validate per quickstart.md steps 2-3: `./gradlew installDebug`, confirm the home-screen/app-drawer icon shows the new dark+orange mark (not the robot placeholder), then force-stop and cold-launch the app and confirm the splash screen's background/mark match `specs/006-home-ui-branding-refresh/assets/splash-1080x2400.png`'s colors and composition, dismissing automatically with no replay on warm resume (depends on T004, T005).

**Checkpoint**: Installed app shows the new launcher icon everywhere (home screen, app drawer,
recents) and the new splash screen on cold start only (quickstart.md steps 2-3).

---

## Phase 5: User Story 2 - Simplified control hint (Priority: P2)

**Goal**: The "Hold a direction to drive" hint no longer renders while connected; the
disconnected-state hint is unchanged (spec.md FR-004).

**Independent Test**: Render `ControlScreen` with `connected = true` and confirm no hint text node
exists; render with `connected = false` and confirm the existing "Waiting for connection to enable
controls" text still renders — independent of US1's sizing, US3's header icon, or US4's icon/splash.

**Note**: Touches the same file as US1 (`ControlScreen.kt`), so this phase is sequenced after
Phase 3 lands rather than run in parallel with it, even though it is a logically independent story.

### Tests for User Story 2

- [X] T007 [US2] Extend `app/src/androidTest/java/com/example/ambullrc/ControlScreenTest.kt` (same file as T002): add an assertion that with `connected = true`, no node with the text "Hold a direction to drive" exists; keep/confirm the existing assertion that with `connected = false`, the "Waiting for connection to enable controls" text still renders unchanged (contracts/ui-contract.md `ControlScreen` guarantees, spec.md FR-004). Depends on T002 (same file).

### Implementation for User Story 2

- [X] T008 [US2] Modify `app/src/main/java/com/example/ambullrc/ui/ControlScreen.kt` (same file as T003): remove the hint `Text` composable for the `connected == true` branch entirely; keep the `connected == false` branch's "Waiting for connection to enable controls" text unchanged. Depends on T003 (same file).

**Checkpoint**: `ControlScreenTest` passes with both the T002 (sizing) and T007 (hint) assertions; connected state shows no hint text, disconnected state is unaffected (quickstart.md step 6).

---

## Phase 6: User Story 3 - Branded header icon (Priority: P2)

**Goal**: A small brand icon appears at the top-left of the header, before the device name, in
every connection state, without displacing the status pill or Retry button (spec.md FR-005).

**Independent Test**: Render `ConnectionStatusBar` in each `ConnectionState` and confirm the brand
icon node exists alongside the device name, status pill, and (when applicable) Retry button, all
unclipped — independent of US1/US2/US4.

### Tests for User Story 3

- [X] T009 [P] [US3] Update `app/src/androidTest/java/com/example/ambullrc/ConnectionStatusBarTest.kt`: add an assertion that a leading brand icon node is present in the header for each of `Idle`, `Connecting`, `Connected`, and `Failed` states, and that the status pill/Retry button (per state) remain present and unclipped alongside it (contracts/ui-contract.md `ConnectionStatusBar` guarantees, spec.md FR-005, SC-003).

### Implementation for User Story 3

- [X] T010 [US3] Modify `app/src/main/java/com/example/ambullrc/ui/ConnectionStatusBar.kt`: add an `Image(painter = painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(28.dp))` (or equivalent `Icon`) as the first child of the existing header `Row`, before the device name `Text`, using `Arrangement.spacedBy` so it doesn't collapse into the existing `SpaceBetween` grouping (research.md Decision 5). No signature change.

**Checkpoint**: `ConnectionStatusBarTest` passes; the header icon renders in every state without wrapping/pushing the status pill or Retry button on a standard phone width (quickstart.md step 4).

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Whole-feature validation once all four stories are implemented.

- [X] T011 Run `./gradlew testDebugUnitTest connectedDebugAndroidTest assembleDebug` (full suite) and fix any cross-story regression.
- [X] T012 Walk through `quickstart.md`'s "Manual validation (emulator or device, no ESP32 required)" section end-to-end on an emulator/device and confirm every numbered step's expected outcome.
- [X] T013 Once a physical phone + paired ESP32 is available, walk through `quickstart.md`'s "On-device validation" section and confirm the enlarged D-pad, hint removal, and header icon all hold up against real hardware while driving (note the result in CLAUDE.md's "Current state" section when done, per this project's existing pattern for on-device checks).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately.
- **Foundational (Phase 2)**: No tasks — nothing blocks the user stories.
- **User Stories (Phase 3-6)**: All can start once Setup completes. US1 (Phase 3) and US4 (Phase
  4) are fully independent of each other and can run in parallel. US2 (Phase 5) must follow US1
  (Phase 3) since both edit `ControlScreen.kt`. US3 (Phase 6) is independent of all other stories
  and can run in parallel with any of them.
- **Polish (Phase 7)**: Depends on all four user stories being complete.

### User Story Dependencies

- **User Story 1 (P1)**: `ControlScreen.kt` (sizing) + its test. No dependency on other stories.
- **User Story 4 (P1)**: `res/drawable-nodpi/*`, `res/values/colors.xml`, `res/values/themes.xml`. No dependency on other stories.
- **User Story 2 (P2)**: `ControlScreen.kt` (hint removal) + its test. Sequenced after US1 (same file), otherwise no logical dependency.
- **User Story 3 (P2)**: `ConnectionStatusBar.kt` + its test. No dependency on other stories (references `ic_launcher_foreground` by resource name regardless of whether US4 has swapped its contents yet).

### Within Each User Story

- Tests are written first and should fail against the pre-change code, then pass once the paired implementation task lands.
- T002 before T003 (US1); T004/T005 before T006 (US4, manual check depends on both resource changes); T007 before T008, both after T002/T003 (US2); T009 before T010 (US3).

### Parallel Opportunities

- T002 [US1 test], T004 & T005 [US4 resources], and T009 [US3 test] can all start in parallel right after Phase 1 — four disjoint sets of files.
- T004 and T005 (both US4) are themselves parallel — one touches `res/drawable-nodpi/`, the other `res/values/colors.xml` + `themes.xml`.
- US2 (T007/T008) cannot start until US1 (T002/T003) lands, since both touch `ControlScreen.kt`.

---

## Parallel Example: Post-Setup Kickoff

```bash
# Once T001 (Setup) is done, these can all start together:
Task: "Update ControlScreenTest.kt for enlarged button sizing (T002)"
Task: "Replace app icon layers with the supplied PNGs (T004)"
Task: "Add splash_background color + windowSplashScreen theme items (T005)"
Task: "Update ConnectionStatusBarTest.kt for the new header icon (T009)"
```

---

## Implementation Strategy

### MVP First (Both P1 Stories)

1. Complete Phase 1: Setup (T001).
2. Complete Phase 2: Foundational — nothing to do, skip straight through.
3. Complete Phase 3: User Story 1 (T002-T003) and Phase 4: User Story 4 (T004-T006) — both P1, independent, can run in parallel.
4. **STOP and VALIDATE**: run `ControlScreenTest` and manually confirm the D-pad fill + new icon/splash on an emulator (quickstart.md steps 2-3, 5).
5. Ship if that alone is the desired increment — the hint text and header icon are unchanged until US2/US3 land.

### Incremental Delivery

1. Setup → nothing blocking.
2. Add User Story 1 → verify independently → D-pad visibly larger.
3. Add User Story 4 → verify independently → new icon + splash screen live.
4. Add User Story 2 → verify independently → connected-state hint gone.
5. Add User Story 3 → verify independently → header icon visible in every state.
6. Phase 7 validates the whole thing together, including the on-device pass.

### Parallel Team Strategy

With more than one implementer:

1. Complete Setup together first (T001) — trivial and blocking.
2. Split by story: one person on US1 → US2 (sequential, same file: T002-T003 then T007-T008), one
   person on US4 (T004-T006), one person on US3 (T009-T010).
3. Each story's checkpoint can be validated and merged independently; Phase 7 runs once all four land.

---

## Notes

- [P] tasks touch different files with no unmet dependencies at the time they'd run.
- [Story] labels map every user-story-phase task back to spec.md's US1/US2/US3/US4 for traceability.
- US1 and US2 share a file (`ControlScreen.kt`) despite being independent user stories in spec.md —
  sequenced rather than parallelized for that reason only; this does not make US2 logically depend
  on US1's outcome.
- Commit after each task or logical group, per the project's normal workflow.
- Stop at any checkpoint (end of Phase 3, 4, 5, or 6) to validate that story independently before continuing.
