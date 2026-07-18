# Feature Specification: ESP32 Bluetooth Connection on Startup

**Feature Branch**: `002-esp32-bluetooth-connection`

**Created**: 2026-07-17

**Status**: Draft

**Input**: User description: "Implement a integration between this app (when the app starts) and an ESP32 microcontroller using bluetooth protocol"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Auto-connect to the ESP32 on startup (Priority: P1)

As the operator, when I launch the app it automatically connects to my RC vehicle's ESP32
over Bluetooth, so the vehicle is ready to receive commands without me having to perform any
manual connection steps.

**Why this priority**: This is the core of the feature and the prerequisite for the entire
project's purpose (sending commands to the ESP32). Until the app can establish a Bluetooth link
to the ESP32, no command can ever reach the vehicle. Everything else in this feature supports or
reports on this connection.

**Independent Test**: Power on the ESP32 in range, launch the app, and confirm that — with no
manual action — the app reaches a "connected" state indicating a live Bluetooth link to the ESP32.

**Acceptance Scenarios**:

1. **Given** the ESP32 is powered on, in range, and known to the phone, **When** the app is
   launched, **Then** the app automatically begins connecting and reaches the "connected" state.
2. **Given** the app has just launched, **When** no operator action is taken, **Then** the
   connection attempt proceeds on its own (no manual "connect" step is required).
3. **Given** the app reaches the "connected" state, **When** the operator inspects the app,
   **Then** the connected state reflects a real, usable link to the ESP32 (not a premature or
   false "connected").

---

### User Story 2 - Know the current connection status (Priority: P1)

As the operator, I can always tell whether the app is connecting, connected, or not connected to
the ESP32, so I know whether the vehicle can currently receive commands.

**Why this priority**: A remote control whose connection status is invisible is unusable — the
operator cannot tell whether commands will reach the vehicle. This observable status is also the
behavior the automated tests assert against, so it is required for the feature to be verifiable.

**Independent Test**: Launch the app under different conditions (ESP32 on/off/out of range) and
confirm the app shows a status that correctly distinguishes connecting, connected, and
not-connected/failed.

**Acceptance Scenarios**:

1. **Given** the app is attempting to connect, **When** the operator views the status, **Then**
   it clearly indicates "connecting".
2. **Given** the link to the ESP32 is established, **When** the operator views the status,
   **Then** it clearly indicates "connected".
3. **Given** the connection has not been established or has failed, **When** the operator views
   the status, **Then** it clearly indicates "not connected" (or failed).

---

### User Story 3 - Recover from connection problems (Priority: P2)

As the operator, when the connection to the ESP32 cannot be established or is lost, the app tells
me instead of silently doing nothing, and I can retry the connection once I've fixed the cause
(e.g., powered on the ESP32 or moved back in range).

**Why this priority**: Bluetooth links routinely fail — the device is off, out of range,
authorization is denied, or the phone's Bluetooth is disabled. Without a clear failure signal and
a way to retry, the operator is stuck. This makes the feature robust in real use, but it is
secondary to first achieving and reporting a successful connection.

**Independent Test**: Launch the app with the ESP32 powered off; confirm the app reports a failure
within a bounded time. Then power on the ESP32, trigger a retry, and confirm the app reaches the
connected state.

**Acceptance Scenarios**:

1. **Given** the ESP32 is unavailable (off, out of range, or not authorized), **When** the app
   attempts to connect, **Then** the app reports a failure within a bounded time and does not hang
   indefinitely.
2. **Given** a failed connection attempt, **When** the operator retries, **Then** the app makes a
   fresh attempt to connect.
3. **Given** the app was connected, **When** the link to the ESP32 is lost, **Then** the app
   reflects the disconnected state rather than continuing to show "connected".

---

### Edge Cases

- **Bluetooth turned off on the phone**: the app must surface that it cannot connect while the
  phone's Bluetooth is disabled, rather than appearing stuck in "connecting".
- **Bluetooth authorization denied**: if the operator declines the app's request to use
  Bluetooth, the app must surface that the connection cannot proceed.
- **ESP32 not known to the phone / not paired**: the app must report that the target device is
  unavailable rather than connecting to an arbitrary device.
- **ESP32 powered off or out of range at startup**: connection fails within a bounded time and the
  operator can retry later.
- **Connection dropped mid-session**: the status returns to disconnected so the operator knows
  commands will no longer reach the vehicle.
- **App relaunched**: on each launch the connection attempt starts fresh.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: On app startup, the app MUST automatically attempt to establish a Bluetooth
  connection to the designated ESP32 without requiring the operator to trigger it manually.
- **FR-002**: The app MUST target a single, known ESP32 device so it does not connect to an
  arbitrary Bluetooth device.
- **FR-003**: The app MUST obtain the authorization required to use Bluetooth before attempting to
  connect; if authorization is denied, the app MUST surface that the connection cannot be
  established.
- **FR-004**: The app MUST expose the current connection state, distinguishing at least:
  connecting, connected, and not connected (including failed).
- **FR-005**: The app MUST enter the "connected" state only when a usable Bluetooth link to the
  ESP32 has actually been established (the reported state must reflect reality).
- **FR-006**: When a connection attempt fails — device unavailable, phone Bluetooth disabled,
  authorization denied, or link error — the app MUST report the failure within a bounded time and
  MUST NOT crash or hang indefinitely.
- **FR-007**: The app MUST allow the operator to retry establishing the connection after a failure.
- **FR-008**: If an established connection to the ESP32 is subsequently lost, the app MUST reflect
  the disconnected state.

### Key Entities

- **Connection State**: the current status of the app-to-ESP32 Bluetooth link. A closed set of
  values distinguishing at minimum: not connected / idle, connecting, connected, and failed. Every
  status the operator sees and every state the tests assert on is one of these values.
- **Target Device**: the single ESP32 the app is meant to connect to, identified so it can be
  distinguished from other Bluetooth devices.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: When the app is launched with the ESP32 powered on, in range, and known to the
  phone, the app reaches the connected state within 10 seconds with no manual connection steps.
- **SC-002**: The operator can determine the current connection status (connecting / connected /
  not connected) at any time, 100% of the time.
- **SC-003**: When the connection cannot be established (device off, out of range, authorization
  denied, or phone Bluetooth disabled), the app reports the failure within 15 seconds and never
  hangs indefinitely.
- **SC-004**: After a failed attempt whose cause has been resolved, the operator can retry and
  reach the connected state.
- **SC-005**: The app never reports "connected" unless a real link to the ESP32 exists (zero false
  "connected" states across the tested scenarios).

## Assumptions

- **Single known target device**: The app connects to one known ESP32, identified by a configured
  Bluetooth device identity, that has already been paired/bonded with the phone. Discovering and
  choosing among multiple devices, and any first-time pairing flow, are out of scope for this
  feature.
- **Scope — connection lifecycle only**: This feature covers establishing, reporting, and
  recovering the Bluetooth connection at startup. Actually transmitting the direction-button
  commands over the connection is a separate follow-up feature that will wire the existing
  command-handling seam (from the direction-buttons feature) to this Bluetooth link.
- **One-way link**: The connection is intended for sending commands to the ESP32. Reading
  telemetry or state back from the ESP32 is out of scope, consistent with the project's scope.
- **Minimal presentation**: Per the project's "function over form" principle, a simple status
  indication is sufficient; no polished connection UI is required. The status must be observable
  enough for the operator to act on and for automated tests to assert.
- **Standard authorization**: The app uses the platform's standard Bluetooth authorization
  prompts; if the operator denies them, connecting is not possible and this is surfaced.
- **Bluetooth transport choice deferred**: The specific Bluetooth mechanism and message framing
  are technical decisions for the implementation plan, not fixed by this specification, and must
  remain minimal per the project's simplicity principle.
