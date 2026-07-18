# Research: Direction Buttons Screen

The Technical Context contained no open `NEEDS CLARIFICATION` items — the stack is fixed
by the existing project. This document records the small decisions that shape the design.

## Decision 1: UI toolkit — Jetpack Compose + Material3

- **Decision**: Build the screen as a Compose `@Composable` using Material3 components
  (`IconButton` + `Icon`), hosted from `MainActivity` via `setContent { AmbullRCTheme { … } }`.
- **Rationale**: The project is already Compose-only (`buildFeatures { compose = true }`,
  Compose BOM, `activity-compose`, `material3` all present; `MainActivity` already uses
  `setContent`). Using Views/XML would add a second UI paradigm — a YAGNI violation.
- **Alternatives considered**: Android Views + XML layout (rejected: not used anywhere in
  the project, would introduce a parallel UI stack for no benefit).

## Decision 2: Arrow icons — Material `KeyboardArrow*` icon set

- **Decision**: Use `Icons.Filled.KeyboardArrowUp`, `KeyboardArrowDown`,
  `KeyboardArrowLeft`, and `KeyboardArrowRight` for the four buttons.
- **Rationale**: These four directional glyphs live in `material-icons-core`, which
  Material3 pulls in transitively, so they are available without adding a dependency —
  consistent with the constitution's "favor defaults, avoid new dependencies" rule.
- **Fallback**: If the build cannot resolve `androidx.compose.material.icons.filled.*`,
  add `androidx.compose.material:material-icons-core` to `gradle/libs.versions.toml` and
  `app/build.gradle.kts` (BOM-versioned, so no explicit version). This is the only
  dependency change that could arise, and only if resolution fails. Prefer the transitive
  path first and confirm at build time.
- **Alternatives considered**: Custom vector drawables in `res/drawable` (rejected:
  more files and effort than the built-in icons for zero UX gain); the Material *Arrow*
  set (`Icons.AutoMirrored.Filled.ArrowBack`, etc.) (rejected: `KeyboardArrow*` gives a
  clean matching up/down/left/right quartet).

## Decision 3: Logging seam — injectable `DirectionLogger`

- **Decision**: `ControlViewModel` receives a `DirectionLogger` (a Kotlin `fun interface`
  with a single `log(direction: Direction)` method). The production default implementation
  calls `android.util.Log.d(...)`; the ViewModel's constructor defaults to it so the
  Composable can create the ViewModel with no arguments.
- **Rationale**: The spec's observable behavior is "a per-direction log record is
  produced." Calling `android.util.Log` directly from the ViewModel would make that
  behavior impossible to assert in a plain JVM unit test (the framework `Log` class is not
  available/stubbed off-device) and would violate Principle II ("logic testable in
  isolation from Android framework classes"). A one-method seam is the minimal change that
  makes the required behavior directly unit-testable — testability is a present, concrete
  requirement (Principle V), not speculative extensibility. This same seam is where the
  future Bluetooth command sender will plug in.
- **Alternatives considered**:
  - ViewModel calls `android.util.Log` directly (rejected: not unit-testable; forces
    reliance on instrumented tests for logic that should be JVM-testable).
  - Full DI framework (Hilt/Koin) to provide the logger (rejected: YAGNI — the
    constitution forbids adding DI containers without a real need; a constructor default
    is sufficient).
  - Expose logged directions only as ViewModel state and assert on state (rejected as the
    *primary* mechanism: the spec explicitly calls for a *log* record; the injected logger
    captures exactly that behavior. State exposure is unnecessary for this feature.)

## Decision 4: Test strategy — JVM unit + Compose UI instrumented

- **Decision**:
  - **Unit (`app/src/test`, JUnit4)**: `ControlViewModelTest` injects a fake
    `DirectionLogger` that records received directions, then asserts each of the four
    `onDirectionTapped` calls logs the matching `Direction`, that repeated calls each log,
    and that a single call logs exactly one direction (no cross-firing). Covers FR-004/005/006
    and SC-002/SC-003.
  - **Integration/UI (`app/src/androidTest`, Compose UI Test)**: `ControlScreenTest` sets
    the `ControlScreen` content with a test `ControlViewModel` (fake logger), verifies all
    four buttons are present via their content descriptions / test tags, performs clicks,
    and asserts the fake logger received the correct directions. Covers US1 (buttons
    present/tappable), FR-001/002/003, and SC-001.
- **Rationale**: Matches the constitution's mandatory unit + integration coverage using
  only the test libraries already configured (`junit`, `androidx.compose.ui.test.junit4`,
  `androidx.junit`, Espresso). The Compose UI test is the appropriate "integration" level
  for a UI-only feature that has no Bluetooth layer yet.
- **Alternatives considered**: Robolectric for on-JVM UI tests (rejected: introduces a new
  test dependency the project doesn't have, against the "favor defaults" rule).

## Decision 5: Control identity for tests — content descriptions + test tags

- **Decision**: Each `IconButton` carries a stable content description (e.g., "Up",
  "Down", "Left", "Right") and a matching `Modifier.testTag` (e.g., `btn_up`). The
  contract file enumerates the exact strings.
- **Rationale**: Content descriptions give the buttons accessible, testable identities
  (also satisfying the "arrow icon indicates direction" acceptance criterion at the
  semantics level); test tags give the instrumented test stable selectors independent of
  on-screen text. No visual/layout polish beyond this is in scope.
- **Alternatives considered**: Selecting buttons by node index/position (rejected: brittle,
  breaks the "no cross-firing" guarantee under layout changes).
