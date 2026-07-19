# Feature Specification: Home Screen UX Redesign

**Feature Branch**: `005-home-screen-ux-redesign`

**Created**: 2026-07-19

**Status**: Draft

**Input**: User description: "Change the whole home section to improve the UX. The parts that are going to be affected are: header with Bluetooth information (1); center, where the buttons are (2); button with a collapsable widget with log info (3). Attached here there's some info on it: a high-fidelity design handoff (RemoteControlApp.dc.html, README.md) covering colors, spacing, typography, and interaction states for all three areas."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See connection status at a glance (Priority: P1)

The driver looks at the top of the screen and immediately understands whether the car is
connecting, connected, or disconnected — through color and a status pill, not just a plain text
line — and, when disconnected, sees a clear way to retry the connection without hunting for it.

**Why this priority**: Every other action on the screen (driving, checking logs) is gated on
knowing the connection state first. This is the first thing the driver looks at and the current
plain-text bar is easy to miss at a glance — the highest-value delight improvement is making this
state unmistakable.

**Independent Test**: Can be fully tested by putting the app through each connection state
(connecting, connected, disconnected) and confirming the status area shows a distinct color, dot,
and label for each, with animation while connecting, and a Retry action that appears only when
disconnected and re-attempts the connection when tapped.

**Acceptance Scenarios**:

1. **Given** the app is attempting to connect, **When** the driver looks at the header, **Then**
   the status pill shows a "connecting" color and label with a pulsing indicator dot, and no
   Retry action is shown.
2. **Given** the app is connected to the ESP32, **When** the driver looks at the header, **Then**
   the device name is shown, the status pill shows a "connected" color and label with a
   steady (non-animated) dot, and no Retry action is shown.
3. **Given** the app is disconnected, **When** the driver looks at the header, **Then** the status
   pill shows a "disconnected" color and label, a Retry action is visible, and the device name area
   shows a neutral placeholder instead of a device name.
4. **Given** the app is disconnected, **When** the driver taps Retry, **Then** the status
   immediately switches to "connecting" and the app re-attempts the connection.

---

### User Story 2 - Drive with clear, responsive controls (Priority: P2)

The driver holds down a direction control and gets instant, unambiguous visual feedback that the
press registered, and can tell at a glance whether the controls are currently usable at all
(i.e., whether the car is connected).

**Why this priority**: Driving is the core purpose of the app (per the project's single-purpose
principle). Feedback quality on the direction controls directly affects how confident and
pleasant the app feels to operate, but it depends on the connection state from Story 1 being
correct first.

**Independent Test**: Can be fully tested by connecting the app, pressing and holding each of the
four direction controls in turn, and confirming each shows a distinct pressed appearance while
held and reverts on release; then disconnecting and confirming all four controls appear disabled
and ignore input.

**Acceptance Scenarios**:

1. **Given** the app is connected, **When** the driver presses and holds a direction control,
   **Then** that control switches to a visually distinct "pressed" appearance for the duration of
   the hold.
2. **Given** a direction control is pressed, **When** the driver releases it, **Then** the control
   reverts to its idle appearance immediately.
3. **Given** the app is not connected, **When** the driver looks at the controls, **Then** all
   four direction controls appear visually disabled (dimmed) and pressing them has no effect.
4. **Given** the app is not connected, **When** the driver looks below the controls, **Then** a
   hint line explains that a connection is needed; **Given** the app is connected, **When** the
   driver looks below the controls, **Then** the hint line instead explains how to drive.

---

### User Story 3 - Inspect activity in a collapsible log panel (Priority: P3)

The driver can tuck the diagnostic log out of the way during normal driving, then pull it open
with a tap or a drag to review recent activity — with each entry legible enough to tell at a
glance what kind of event it was (a sent command, a received acknowledgement, a connection event,
or a general app event).

**Why this priority**: The log is a diagnostic aid, not part of the core driving loop, so it's the
lowest-priority of the three areas — but the current panel is a raw scrolling text box that is
hard to scan and always takes up screen space, so it's the area furthest from feeling polished.

**Independent Test**: Can be fully tested by generating a handful of log entries, confirming the
collapsed panel shows a live entry count, expanding it via both a tap and a drag gesture and
confirming it opens smoothly and auto-scrolls to the newest entry, and confirming entries are
visually distinguishable by category.

**Acceptance Scenarios**:

1. **Given** the log panel is collapsed, **When** the driver looks at it, **Then** it shows a
   compact strip with a live count of captured log lines and no individual entries.
2. **Given** the log panel is collapsed, **When** the driver taps it, **Then** it expands smoothly
   to show the scrolling log list; **When** the driver taps it again, **Then** it collapses back.
3. **Given** the log panel is collapsed, **When** the driver drags it upward past the midpoint of
   its travel range and releases, **Then** it snaps fully open; **When** dragged back down past
   the midpoint and released, **Then** it snaps fully closed.
4. **Given** the log panel is expanded and a new entry arrives, **When** the driver is looking at
   the list, **Then** the list auto-scrolls to reveal the newest entry.
5. **Given** the log panel is expanded, **When** the driver reads an entry, **Then** they can tell
   whether it is a sent command, a received acknowledgement, a connection/adapter event, or a
   general app event by its color/tag alone, without reading the full message text.

---

### Edge Cases

- What happens if the driver is holding a direction control down at the exact moment the
  connection drops? The control MUST revert to its disabled appearance and stop being treated as
  pressed, the same as a normal release.
- What happens if the connection state flips rapidly (e.g., connecting → disconnected →
  connecting) before an animation finishes? The status pill MUST always reflect the latest actual
  state, cancelling/replacing any in-progress visual transition rather than queuing them.
- What happens when the device name is too long to fit the header? It MUST truncate with an
  ellipsis rather than wrapping or overflowing the screen.
- What happens when the driver starts a drag on the log panel but moves less than a small
  threshold before releasing? It MUST be treated as a tap (toggle open/closed), not a drag.
- What happens once the log history exceeds its retained cap? The oldest entries MUST drop off
  silently as new ones arrive; the driver-visible count reflects only what's retained.
- What happens if the driver taps Retry while a previous connection attempt is still in flight?
  The app MUST treat it as a fresh retry (existing attempt is superseded, state stays/returns to
  "connecting").

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The header MUST display the connection state as a colored pill indicator with a
  distinct background color, dot color, and label for each of the three driver-facing states:
  connecting, connected, and disconnected.
- **FR-002**: The connecting indicator MUST animate (pulse) to convey that a connection attempt is
  in progress; the connected and disconnected indicators MUST NOT animate.
- **FR-003**: The header MUST show the connected device's name when connected, and a neutral
  placeholder when not connected, truncating with an ellipsis if the name doesn't fit.
- **FR-004**: A Retry action MUST be shown in the header only while disconnected, and activating
  it MUST re-attempt the connection and switch the status indicator to "connecting".
- **FR-005**: The four direction controls MUST be laid out in a cross/D-pad arrangement around a
  decorative, non-interactive center hub.
- **FR-006**: Each direction control MUST show a visually distinct "pressed" appearance for as
  long as it is held, reverting to its idle appearance immediately on release.
- **FR-007**: Direction controls MUST appear visually disabled (dimmed) and MUST NOT respond to
  presses while the app is not connected.
- **FR-008**: A short hint line below the controls MUST tell the driver whether they can drive now
  or need to wait for a connection, reflecting the current connection state.
- **FR-009**: The log panel MUST be presented as a bottom sheet that can be collapsed to a minimal
  strip or expanded to a scrolling list, both states reachable from the other via a single tap or
  drag gesture.
- **FR-010**: Transitions between the log panel's collapsed and expanded states MUST animate
  smoothly rather than snapping instantly.
- **FR-011**: While collapsed, the log panel MUST still display a live count of captured log
  entries.
- **FR-012**: While expanded, each log entry MUST display a timestamp, a short category tag, and
  its message, with color distinguishing at least: sent commands, received acknowledgements,
  connection/adapter events, and general app events.
- **FR-013**: While expanded, the log list MUST auto-scroll to show the newest entry as new
  entries arrive.
- **FR-014**: The number of retained log entries MUST remain capped so memory does not grow
  unbounded during long sessions (matching the existing cap behavior).
- **FR-015**: All existing underlying behavior — real connection/retry/disconnect handling,
  direction press/release command sending, and log capture — MUST be preserved; this feature
  changes presentation only, not what the app does or when it sends commands.

### Key Entities *(include if feature involves data)*

- **Connection Status Indicator**: The visual representation of connection state (connecting /
  connected / disconnected) — a derived view, not new state; it maps the app's existing connection
  state to a color, label, dot, and animation.
- **Log Entry**: A single captured event line. Today this is a plain message string; this feature
  requires each entry to also carry (or be classified into) a timestamp, a category — sent
  command, received acknowledgement, connection/adapter event, or general app event — so the log
  panel can color-code and tag it.
- **Direction Control Press State**: Which single direction control, if any, is currently held —
  a derived view of existing press/release state, used to drive the pressed-appearance highlight.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A driver can determine the current connection state (connecting, connected, or
  disconnected) purely from color and dot, without reading the text label.
- **SC-002**: A driver sees the pressed-state highlight on a direction control the instant they
  touch it and sees it revert the instant they release, with no perceptible delay.
- **SC-003**: A driver can fully open or fully close the log panel using exactly one gesture — a
  single tap on the collapsed strip, or a single drag past the midpoint.
- **SC-004**: A driver scanning the expanded log list can tell apart sent commands, received
  acknowledgements, and connection events by color alone, without reading the message text.
- **SC-005**: The header, control area, and log panel share one consistent visual language
  (palette, spacing, motion) — no one area looks visually disconnected from the other two.

## Assumptions

- The app's underlying connection mechanism (Classic Bluetooth RFCOMM/SPP, single bonded device
  matched by name, per the project constitution) is unchanged by this feature — the design
  reference's mention of BLE/GATT is treated as illustrative interaction language from the
  prototype, not a requirement to change the transport.
- The existing four connection states (`Idle`, `Connecting`, `Connected`, `Failed`) map to the
  three driver-facing status categories as follows: `Connecting` → "connecting"; `Connected` →
  "connected"; both `Idle` and `Failed` → "disconnected" (with Retry shown), since `Idle` is a
  transient pre-connect state the driver should be able to recover from the same way as a failure.
- The specific color palette, spacing, and motion timing in the design reference are treated as
  the intended visual direction (per the project constitution's call for delightful UX), not as
  pixel-exact mandates — reasonable adaptation to Android's native theming is acceptable as long
  as the resulting feel matches (dark, high-contrast, calm accent-driven feedback).
- "Category" for a log entry (sent / received / connection-adapter / general) is a reasonable
  reinterpretation of the design's `TX` / `RX` / `BLE` / `APP` source tags, mapped onto this app's
  existing log call sites rather than introduced as new logging.
- The retained log entry cap stays whatever the app already uses internally; this feature does not
  require changing that number, only rendering what's retained more legibly.
- This is a single-screen app (per the constitution's single-purpose principle) — no navigation,
  multiple screens, or settings are introduced by this redesign.
