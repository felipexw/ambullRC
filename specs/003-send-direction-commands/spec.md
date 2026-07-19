# Feature Specification: Send Direction Commands to ESP32

**Feature Branch**: `003-send-direction-commands`

**Created**: 2026-07-18

**Status**: Draft

**Input**: User description: "every time the user taps on a direction button, it should send just a string to the esp32 bluetooth connected device"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Tapping a direction button commands the vehicle (Priority: P1)

As the operator, when I tap a direction button (up, down, left, right) while the app is
connected to the ESP32, the app immediately sends that direction to the vehicle over the
existing Bluetooth link, so the vehicle responds to my input.

**Why this priority**: This is the entire purpose of the app — every prior feature (the
direction buttons UI, the Bluetooth auto-connect) exists solely to support this moment. Without
this, the app cannot actually control the vehicle.

**Independent Test**: With the app connected to a paired ESP32, tap each of the four direction
buttons in turn and confirm the ESP32 receives one distinct message per tap, matching the
direction tapped.

**Acceptance Scenarios**:

1. **Given** the app is connected to the ESP32, **When** the operator taps the Up button,
   **Then** a message identifying "up" is sent over the Bluetooth connection.
2. **Given** the app is connected to the ESP32, **When** the operator taps the Down, Left, or
   Right button, **Then** a message identifying that specific direction is sent, distinguishable
   from the other three.
3. **Given** the app is connected, **When** the operator taps a direction button twice in a row,
   **Then** two separate messages are sent (one per tap).

---

### User Story 2 - Tapping while not connected does not crash or hang (Priority: P2)

As the operator, if I tap a direction button while the app is not connected to the ESP32, the
app does not crash and does not appear to hang — it behaves predictably so I understand the
vehicle did not receive the command.

**Why this priority**: The connection can be lost or not yet established (see feature 002), and
the operator can still tap buttons in that state. The app must degrade gracefully rather than
fail.

**Independent Test**: With the app in a not-connected state, tap a direction button and confirm
the app remains responsive and reflects that no command was delivered.

**Acceptance Scenarios**:

1. **Given** the app is not connected to the ESP32, **When** the operator taps a direction
   button, **Then** the app does not crash and no message is transmitted.
2. **Given** a tap occurred while not connected, **When** the operator later reconnects, **Then**
   subsequently tapped directions are sent normally (the earlier missed tap is not queued or
   replayed).

---

### Edge Cases

- **Connection drops mid-tap**: if the Bluetooth link is lost at the exact moment a tap is
  processed, the send attempt fails and is treated the same as tapping while not connected (no
  crash, no silent retry loop).
- **Rapid repeated taps**: each tap produces its own independent send attempt; taps are not
  batched, deduplicated, or coalesced.
- **Send failure at the transport level** (e.g., Bluetooth socket write error): the app does not
  crash and does not treat the failure as a fatal/unrecoverable error.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST send a message to the ESP32 over the existing Bluetooth connection
  every time the operator taps a direction button (Up, Down, Left, or Right).
- **FR-002**: Each of the four directions MUST produce a distinct, unambiguous message so the
  ESP32 can tell them apart.
- **FR-003**: The system MUST send exactly one message per tap — a tap MUST NOT be silently
  dropped, duplicated, or batched with other taps under normal (connected) operation.
- **FR-004**: The message content MUST be limited to identifying which direction was tapped; it
  MUST NOT carry additional data (speed, angle, timestamps, etc.) beyond the direction itself.
- **FR-005**: When a tap occurs while the app is not connected to the ESP32, the system MUST
  silently drop the tap — no message is sent, no additional operator-visible feedback is shown
  beyond the existing connection status indicator, and no attempt is made to queue or replay the
  tap once reconnected.
- **FR-006**: The system MUST NOT crash or hang the UI when a send attempt fails for any reason
  (not connected, socket error, etc.).
- **FR-007**: The system MUST NOT require any operator action beyond the existing button tap to
  transmit a command (no separate "send" or "confirm" step).

### Key Entities

- **Direction Command**: The outbound message representing a single button tap — identifies
  exactly one of Up, Down, Left, or Right. Carries no additional data.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of direction-button taps made while connected result in exactly one message
  reaching the ESP32.
- **SC-002**: The ESP32 (or a test double standing in for it) can distinguish all four directions
  from the message content alone, with zero ambiguity between them.
- **SC-003**: Tapping a direction button while disconnected never crashes the app or leaves the
  UI unresponsive, across 100% of attempts.
- **SC-004**: The time between a tap and the corresponding message being handed to the Bluetooth
  connection is not perceptible to the operator (effectively immediate).

## Assumptions

- This feature reuses the Bluetooth connection already established by feature
  002-esp32-bluetooth-connection; it does not add new connection-management behavior.
- "Just a string" means each direction is encoded as a simple, distinct text token (e.g., plain
  words or short codes); the exact wire format/encoding is a technical decision left to the
  implementation plan, per the project constitution's guidance that protocol framing is a
  planning-time decision.
- A tap maps one-to-one to one send attempt; holding a button down does not stream repeated
  commands (matches the existing single-tap `onClick` behavior of the direction buttons).
- No acknowledgment or response is expected back from the ESP32 for a sent command, consistent
  with the project's one-way remote-control scope.
- Only the four existing directions (Up, Down, Left, Right) are in scope; no new commands (e.g.,
  stop, speed levels) are introduced by this feature.
