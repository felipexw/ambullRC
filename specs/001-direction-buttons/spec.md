# Feature Specification: Direction Buttons Screen

**Feature Branch**: `001-direction-buttons`

**Created**: 2026-07-17

**Status**: Draft

**Input**: User description: "Build a screen with four buttons: one with arrow icon up, another with one arrow icon down, one for the left and the last one for the right. each button should log when tapped."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Operate directional controls (Priority: P1)

As the operator of the RC vehicle, I open the app to a single control screen that
presents four directional buttons — up, down, left, and right — so I have a clear,
tappable control for each direction the vehicle can be commanded to move.

**Why this priority**: This is the entire feature and the foundation of the app's
control surface. Without a screen that presents the four directional controls, there
is nothing to operate. Every later capability (sending commands over Bluetooth to the
ESP32) attaches to these controls.

**Independent Test**: Launch the app, observe that the control screen appears with
exactly four buttons — up, down, left, right — each showing the correct arrow icon,
and confirm each button can be tapped. Fully deliverable and demonstrable on its own.

**Acceptance Scenarios**:

1. **Given** the app is launched, **When** the control screen is displayed, **Then**
   four directional buttons are visible: up, down, left, and right.
2. **Given** the control screen is displayed, **When** the operator looks at each
   button, **Then** each button shows an arrow icon indicating its direction (up
   arrow, down arrow, left arrow, right arrow respectively).

---

### User Story 2 - Confirm each tap is registered (Priority: P1)

As the operator, when I tap any of the four directional buttons, the app records that
the specific direction was activated, so I (and the developer) can verify that user
input is being received and correctly distinguished per direction.

**Why this priority**: A control that does not confirm it registered input is not
verifiably working. Recording each tap is the observable behavior that proves the
control surface functions, and it is the seam the future Bluetooth command layer will
hook into. Per the project constitution, this recorded behavior is what the automated
tests assert against.

**Independent Test**: Tap each button in turn and confirm that a distinct, direction-
specific record is produced for each tap (up produces an "up" record, down a "down"
record, and so on), and that repeated taps each produce a record.

**Acceptance Scenarios**:

1. **Given** the control screen is displayed, **When** the operator taps the up
   button, **Then** a record identifying the "up" direction is produced.
2. **Given** the control screen is displayed, **When** the operator taps the down
   button, **Then** a record identifying the "down" direction is produced.
3. **Given** the control screen is displayed, **When** the operator taps the left
   button, **Then** a record identifying the "left" direction is produced.
4. **Given** the control screen is displayed, **When** the operator taps the right
   button, **Then** a record identifying the "right" direction is produced.
5. **Given** the control screen is displayed, **When** the operator taps the same
   button multiple times, **Then** each tap produces its own record.

---

### Edge Cases

- **Rapid repeated taps**: Each tap on a button MUST produce its own record; the app
  MUST NOT silently collapse or drop rapid successive taps of the same direction.
- **No accidental cross-firing**: Tapping one button MUST record only that direction
  and MUST NOT produce a record for any other direction.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The app MUST present a single control screen containing exactly four
  directional buttons: up, down, left, and right.
- **FR-002**: Each button MUST display an arrow icon corresponding to its direction —
  an up-pointing arrow, a down-pointing arrow, a left-pointing arrow, and a
  right-pointing arrow, respectively.
- **FR-003**: Each button MUST be independently tappable.
- **FR-004**: Tapping a button MUST produce a log record that identifies which
  direction was tapped, distinct from the other three directions.
- **FR-005**: Each individual tap MUST produce its own log record (repeated taps are
  each recorded).
- **FR-006**: Tapping one button MUST NOT produce a log record for any other
  direction.

### Key Entities

- **Direction**: The set of four discrete directions the control screen exposes — up,
  down, left, right. Each button on the screen corresponds to exactly one direction,
  and each logged record references exactly one direction.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On launching the app, the operator sees all four directional buttons,
  each with the correct directional arrow icon, 100% of the time.
- **SC-002**: Tapping any of the four buttons produces exactly one direction-correct
  log record per tap, verified across all four directions.
- **SC-003**: In a sequence of taps across mixed directions, every tap is recorded and
  every record matches the direction of the button that was tapped (0 mismatched or
  dropped records).

## Assumptions

- **Logging destination**: "Log when tapped" is satisfied by writing to the standard
  application log (device log / logcat). No on-screen display of tap history, no
  persistence to storage, and no export is required for this feature.
- **Scope boundary — no Bluetooth yet**: This feature covers only the control screen
  and its per-direction logging. Sending commands to the ESP32 over Bluetooth is
  explicitly out of scope here and will be a separate feature; the per-direction tap
  handling is the seam that later Bluetooth work will attach to.
- **Direction meaning**: Up/down/left/right are treated as the raw directional inputs.
  Their eventual mapping to vehicle actuators (throttle forward/reverse, steering
  left/right per the project's servo + DC motor setup) is deferred to the future
  command-transmission feature and is not defined here.
- **Presentation**: Per the project constitution, visual polish is not a goal. The
  screen only needs the four labeled/iconed buttons arranged clearly enough to
  identify and tap each direction; layout aesthetics beyond that are out of scope.
- **Single screen, single operator**: The app shows one control screen; there is no
  navigation, no authentication, and no multi-user concern for this feature.
