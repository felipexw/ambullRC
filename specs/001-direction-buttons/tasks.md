---
description: "Task list for Direction Buttons Screen"
---

# Tasks: Direction Buttons Screen

**Input**: Design documents from `/specs/001-direction-buttons/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/ui-contract.md, quickstart.md

**Tests**: REQUIRED for this project. The constitution (Principle V) mandates unit tests for
ViewModel logic and integration-level (Compose UI instrumented) coverage before a feature is
done, so test tasks are included and are not optional.

**Organization**: Tasks are grouped by user story. Both stories are Priority P1; User Story 1
(the visible screen) is the MVP slice, User Story 2 adds the automated proof that taps are
logged correctly.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1, US2)
- All paths are repo-relative

## Path Conventions

Single Android module `:app`:

- Main code: `app/src/main/java/com/example/ambullrc/`
- Unit tests (JVM): `app/src/test/java/com/example/ambullrc/`
- Instrumented tests: `app/src/androidTest/java/com/example/ambullrc/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm the existing project builds and the required Material icons resolve before
writing feature code.

- [X] T001 Verify `androidx.compose.material.icons.filled.KeyboardArrowUp` / `KeyboardArrowDown` / `KeyboardArrowLeft` / `KeyboardArrowRight` resolve under the current Material3 + Compose BOM setup by running `./gradlew :app:assembleDebug`; if they do not resolve, add `androidx.compose.material:material-icons-core` to `gradle/libs.versions.toml` and `app/build.gradle.kts` (BOM-versioned, no explicit version) per the research.md fallback, then re-run the build to green.

**Checkpoint**: Baseline build is green and the four arrow icons are importable.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared MVVM core (Model + logging seam + ViewModel) that BOTH user stories depend on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T002 Create `Direction` enum (`UP`, `DOWN`, `LEFT`, `RIGHT`) in `app/src/main/java/com/example/ambullrc/model/Direction.kt` per data-model.md.
- [X] T003 Create `DirectionLogger` `fun interface` with `fun log(direction: Direction)` plus an Android `Log.d`-backed default implementation in `app/src/main/java/com/example/ambullrc/viewmodel/DirectionLogger.kt` per contracts/ui-contract.md (depends on T002).
- [X] T004 Create `ControlViewModel` with `fun onDirectionTapped(direction: Direction)` that calls `logger.log(direction)` exactly once, constructor taking a `DirectionLogger` defaulting to the Android-backed impl, in `app/src/main/java/com/example/ambullrc/viewmodel/ControlViewModel.kt` per contracts/ui-contract.md (depends on T002, T003).

**Checkpoint**: Shared core compiles — user stories can now proceed.

---

## Phase 3: User Story 1 - Operate directional controls (Priority: P1) 🎯 MVP

**Goal**: A single screen shows four directional buttons (up/down/left/right) with matching arrow
icons, each independently tappable.

**Independent Test**: Launch the app; confirm exactly four buttons appear with the correct arrow
icons and each can be tapped (verified by the instrumented UI test asserting presence + tappability).

### Tests for User Story 1 ⚠️ (write first, expect failure before T006/T007)

- [X] T005 [US1] Write instrumented Compose UI test `ControlScreenTest` in `app/src/androidTest/java/com/example/ambullrc/ControlScreenTest.kt` that sets `ControlScreen` content (with a `ControlViewModel` backed by a fake `DirectionLogger`) and asserts all four buttons exist and are tappable, selected by the content descriptions / test tags in contracts/ui-contract.md (`btn_up`/"Up", `btn_down`/"Down", `btn_left`/"Left", `btn_right`/"Right"). Covers FR-001/002/003, SC-001.

### Implementation for User Story 1

- [X] T006 [US1] Implement `ControlScreen` composable in `app/src/main/java/com/example/ambullrc/ui/ControlScreen.kt`: render four Material3 `IconButton`s, one per `Direction`, each with its `KeyboardArrow*` icon, content description, and `Modifier.testTag` per contracts/ui-contract.md, in a simple functional layout; each button calls `viewModel.onDirectionTapped(<its Direction>)` and does nothing else (depends on T004).
- [X] T007 [US1] Host `ControlScreen` from `MainActivity` in `app/src/main/java/com/example/ambullrc/MainActivity.kt`, replacing the `Greeting`/`GreetingPreview` sample inside the existing `AmbullRCTheme { Scaffold { … } }` (depends on T006).

**Checkpoint**: The screen is fully functional — four labeled arrow buttons, all tappable — and T005 passes. Tapping already logs via the foundational ViewModel's default Android logger; US2 proves it.

---

## Phase 4: User Story 2 - Confirm each tap is registered (Priority: P1)

**Goal**: Prove that tapping any button produces exactly one direction-correct log record, that
repeated taps each log, and that no tap cross-fires to another direction.

**Independent Test**: Drive `ControlViewModel.onDirectionTapped` (unit) and button clicks (UI) with
a fake `DirectionLogger`; confirm every tap yields exactly one matching record and none mismatched.

### Tests for User Story 2 ⚠️

- [X] T008 [P] [US2] Write JVM unit test `ControlViewModelTest` in `app/src/test/java/com/example/ambullrc/ControlViewModelTest.kt` using a fake `DirectionLogger` that records received directions; assert that `onDirectionTapped(UP|DOWN|LEFT|RIGHT)` logs the matching `Direction` (FR-004), that N calls produce N records in call order (FR-005), and that a single call logs exactly one record and never another direction (FR-006). Covers SC-002, SC-003.
- [X] T009 [US2] Extend `ControlScreenTest` in `app/src/androidTest/java/com/example/ambullrc/ControlScreenTest.kt` to assert that clicking each of the four buttons drives the fake `DirectionLogger` with the correct `Direction` and no cross-firing (FR-004/006, SC-002/003) (depends on T005).

**Checkpoint**: Both unit and UI tests confirm correct per-direction logging. Feature is verifiably complete.

---

## Phase 5: Polish & Validation

**Purpose**: Run the full suite and the manual smoke check from quickstart.md (Definition of Done).

- [X] T010 [P] Run `./gradlew :app:testDebugUnitTest` and `./gradlew :app:connectedDebugAndroidTest`; confirm all unit and instrumented tests pass (quickstart.md Definition of Done).
- [X] T011 Manual Logcat smoke check per quickstart.md: install the app, tap each button (including repeated taps), and confirm one log record per tap with the correct direction.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS both user stories.
- **User Story 1 (Phase 3)**: Depends on Foundational.
- **User Story 2 (Phase 4)**: Depends on Foundational. Independent of US1 for its unit test (T008); its UI test (T009) extends the US1 test file (T005) so runs after it.
- **Polish (Phase 5)**: Depends on both user stories being complete.

### Task-level dependencies

- T002 → T003 → T004 (each builds on the previous type).
- T004 → T006 → T007 (screen needs the ViewModel; MainActivity hosts the screen).
- T005 → T009 (T009 extends the same test file created in T005).
- T008 depends only on T004 (foundational ViewModel) — independent of all US1 tasks.

### Within Each User Story

- Tests are written before/alongside implementation and must pass at the checkpoint.
- Foundational types before ViewModel; ViewModel before Composable; Composable before host Activity.

## Parallel Opportunities

- **T008 [P]** (ViewModel unit test) is independent of every US1 task once Foundational (T004) is
  done — it can be written in parallel with US1's T005/T006/T007.
- **T010 [P]** runs after all implementation is complete.
- Foundational tasks (T002→T003→T004) are a dependency chain and are NOT parallel with each other.

### Parallel Example

```bash
# After Foundational (T004) completes, US1 and the US2 unit test can proceed together:
Task: "T005 [US1] Instrumented UI test: four buttons present + tappable (ControlScreenTest.kt)"
Task: "T008 [US2] Unit test: onDirectionTapped logs correct direction, repeats, no cross-fire (ControlViewModelTest.kt)"
```

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1 Setup (T001).
2. Phase 2 Foundational (T002–T004).
3. Phase 3 User Story 1 (T005–T007).
4. **STOP and VALIDATE**: launch the app — four arrow buttons, all tappable; T005 passes. This is a
   demoable MVP (tapping already logs via the default Android logger).

### Incremental Delivery

1. Setup + Foundational → shared core ready.
2. Add US1 → visible, tappable screen → demo (MVP).
3. Add US2 → automated proof taps log correctly (unit + UI) → feature verifiably done.
4. Polish → full suite green + Logcat smoke check.

## Notes

- [P] = different files, no dependencies on incomplete tasks.
- Every task lists an exact file path.
- Keep everything minimal per the constitution (YAGNI): no Bluetooth, no persistence, no DI framework,
  no navigation — those are out of scope for this feature.
- Commit after each task or logical group.
