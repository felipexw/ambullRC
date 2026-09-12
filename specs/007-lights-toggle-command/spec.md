# Feature Specification: Lights Toggle Command

**Feature Branch**: `[007-lights-toggle-command]`

**Created**: 2026-09-11

**Status**: Draft

**Input**: User description: "add a new command (button should be between the `down arrow and the left`) which will be used to switch on/off the lights from the RC"

## Clarifications

### Session 2026-09-11

- Q: Does the lights icon depict the CURRENT confirmed light state, or the NEXT state (what tapping will switch to)? → A: Icon shows the CURRENT confirmed light state (e.g. bright bulb = on, dim/outline bulb = off).
- Q: How does the app obtain/refresh the confirmed light state from the ESP32? → A: Continuous polling — the app repeatedly queries the ESP32 for light state on a fixed interval the whole time it's connected.
- Q: What should the lights control show when there is no fresh confirmed state yet (first connect) or a state query fails? → A: Disable the control (same as disconnected) while continuing to display the last known confirmed state's icon; no separate "unknown" indicator.
- Q: Does tapping the control optimistically flip the icon immediately, or does the icon wait for the next poll to confirm the change? → A: Confirmed-only — the icon does not change on tap; it only updates once the next poll reports the new state.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Toggle the RC's lights on and off (Priority: P1)

While driving the RC, the user wants to turn its lights on (e.g. entering a
dim area, or just for effect) or off again, using a dedicated control on the
control screen, without interrupting steering or throttle input.

**Why this priority**: This is the entire feature — a single new control that
sends a new command. There is no smaller independently-valuable slice.

**Independent Test**: With the app connected to the ESP32, tap the lights
control once and confirm the on-state command is sent, then confirm the
control's icon updates to "on" once the app's next state poll reports it;
tap again and confirm the same for the off-state command and icon.

**Acceptance Scenarios**:

1. **Given** the app is connected and the lights icon currently shows off,
   **When** the user taps the lights control, **Then** an "on" command is
   sent to the ESP32, and the icon updates to show "on" once a subsequent
   poll confirms the ESP32 is now on.
2. **Given** the app is connected and the lights icon currently shows on,
   **When** the user taps the lights control, **Then** an "off" command is
   sent to the ESP32, and the icon updates to show "off" once a subsequent
   poll confirms the ESP32 is now off.
3. **Given** the app is connected, **When** the user holds a directional
   control and taps the lights control mid-hold, **Then** the directional
   command keeps being sent without interruption and the lights command is
   also sent.

---

### User Story 2 - Lights control reflects connection state (Priority: P2)

The user should not be able to trigger a lights command while the app is not
connected to the ESP32, consistent with how the directional controls already
behave.

**Why this priority**: Keeps the new control consistent with existing
behavior and prevents confusing no-op taps; small effort on top of Story 1.

**Independent Test**: With the app disconnected, confirm the lights control
is visibly dimmed/disabled and tapping it has no effect (no command sent, no
state change).

**Acceptance Scenarios**:

1. **Given** the app is disconnected, **When** the user taps where the
   lights control is, **Then** no command is sent and the lights state does
   not change.
2. **Given** the app just connected and no state poll has succeeded yet,
   **When** the control screen is shown, **Then** the lights control stays
   disabled, showing the last known confirmed state (or "off" if none exists
   this session), until the first successful poll response arrives.

---

### Edge Cases

- What happens if the user taps the lights control rapidly, faster than the
  polling interval? Each tap sends a toggle command for the icon's current
  (last-confirmed) state at the time of that tap; because the icon does not
  move until a poll confirms it, taps that land before any poll response can
  request the same transition more than once or request an outdated
  transition — the ESP32's actual final state, as reflected by the next poll,
  is authoritative regardless.
- What happens if the connection drops while the lights are on? Polling stops
  along with everything else; the control is disabled per FR-005 and keeps
  showing the last confirmed state until reconnected and a fresh poll
  succeeds.
- What happens on app restart? The lights control shows "off" and stays
  disabled until connected and the first state poll succeeds, since nothing
  is persisted locally between app runs (see Assumptions).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The control screen MUST provide a lights control positioned
  between the Down and Left directional controls.
- **FR-002**: Each tap of the lights control MUST send exactly one command to
  the ESP32 requesting the opposite of the last confirmed light state (off→on
  or on→off); the tap MUST NOT change the icon by itself.
- **FR-003**: The lights control's icon MUST only change in response to a
  poll confirming the new state (per FR-007), not directly as a result of the
  tap that requested it — there may be a brief delay between tap and the
  icon updating, bounded by the polling interval.
- **FR-004**: The lights control's icon MUST depict the current confirmed
  light state (on vs. off), not the state a tap would switch it to, and this
  MUST be visually distinct from its enabled/disabled appearance.
- **FR-005**: The lights control MUST be disabled (non-interactive, visually
  dimmed) whenever the app is disconnected from the ESP32, consistent with
  the existing directional controls, and MUST also be disabled whenever a
  light-state query has not yet succeeded (first connect) or has failed,
  even if the underlying connection is otherwise still up.
- **FR-006**: Toggling the lights control MUST NOT interrupt, delay, or
  alter the repeating stream of commands sent for any directional control
  that is currently held.
- **FR-007**: While connected, the app MUST continuously query the ESP32 for
  the current light state on a regular interval, and the lights control's
  icon MUST update to match the most recently received value — the ESP32,
  not the app's own toggle history, is the source of truth for what the icon
  shows.
- **FR-008**: Before the first state query response has been received after
  connecting, or after a state query fails, the lights control's icon MUST
  continue showing the last known confirmed state (or "off" if none has ever
  been confirmed this session) while the control is disabled per FR-005 —
  no separate "unknown" visual indicator is shown.

### Key Entities

- **Light state**: The ESP32's actual current on/off state for the lights, as
  most recently reported back to the app over Bluetooth. The app treats this
  polled value, not its own toggle history, as authoritative for what the
  lights control's icon displays.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can turn the RC's lights on or off with a single tap,
  with the control's icon reflecting the ESP32's confirmed new state within
  one polling interval after the tap.
- **SC-002**: The lights control appears in the same fixed position (between
  Down and Left) every time the control screen is shown, with 100%
  consistency.
- **SC-003**: Toggling the lights while a directional control is held causes
  no observable interruption or delay in the directional command stream.
- **SC-004**: While disconnected, 100% of taps on the lights control produce
  no command and no visible state change.

## Assumptions

- The ESP32 firmware will be updated (outside this app's codebase) to
  recognize a new lights on/off command; wiring an actual light to the ESP32
  is out of scope for this app-side feature.
- The lights toggle is a simple on/off switch, not a hold-to-activate control
  like the directional buttons — it does not need to keep resending while
  held, since lights don't need a "stop if signal goes quiet" safety
  behavior the way a moving motor does.
- The polling interval for light-state queries is not user-configurable and
  is chosen for planning to balance freshness against added Bluetooth
  traffic alongside the existing directional command stream; the exact
  value is a planning-time decision, not a product requirement.
- Nothing about light state is persisted across app restarts; the app relies
  entirely on the ESP32 (via polling) to learn the real state after each
  connect.
- This feature adds a third command class (lights) alongside steering and
  throttle, **and** introduces the app's first read-back-from-ESP32 (state
  polling) path. The project constitution's Hardware & Communication Scope
  section currently states the ESP32 "controls exactly two actuators", that
  the command surface "MUST NOT be generalized into an arbitrary
  multi-channel command system", and that "Reading telemetry/state back from
  the ESP32 is out of scope unless a future feature explicitly requires it."
  This feature explicitly requires state read-back and a third actuator —
  implementing it as specified will require amending that section (or
  explicitly justifying the deviation) before `/speckit-plan` can pass its
  Constitution Check gate.
