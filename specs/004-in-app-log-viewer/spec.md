# Feature Specification: In-App Log Viewer Widget

**Feature Branch**: `004-in-app-log-viewer`

**Created**: 2026-07-18

**Status**: Draft

**Input**: User description: "Add a small widget in the app home so that I can see everything that is logged in the client (app), including info from android, std.out, etc. this widget should be right below the arrows (control)."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See what the app is doing without a computer (Priority: P1)

While testing the app against a real ESP32, the operator wants to see what the app is logging (connection status changes, direction commands sent or dropped, errors) directly on the phone screen, without connecting the phone to a computer and reading Logcat.

**Why this priority**: This is the entire point of the feature — it's the one capability that makes the rest of the widget useful. Without it there's no MVP.

**Independent Test**: Can be fully tested by opening the app on a phone with no computer attached, tapping a direction button or triggering a connection change, and confirming a corresponding message appears on screen below the control arrows.

**Acceptance Scenarios**:

1. **Given** the app is open and connected to the ESP32, **When** the operator taps a direction button, **Then** a message describing that tap's outcome (command sent, or dropped because disconnected) appears in the widget.
2. **Given** the app is open, **When** the connection to the ESP32 changes state (connecting, connected, failed, lost), **Then** a message describing that change appears in the widget.
3. **Given** the widget already has more messages than fit on screen, **When** a new message arrives, **Then** the newest message is visible without the operator needing to do anything.

---

### User Story 2 - Review the sequence of recent events, not just the latest one (Priority: P2)

The operator wants to scroll back through the last several events (not just see the single most recent line) to understand the order things happened in when diagnosing a problem — e.g. "did the tap happen before or after the connection dropped?"

**Why this priority**: Adds real diagnostic value on top of P1 (a single-line "latest message only" display is far less useful for debugging a sequence of events), but the feature is still usable without it.

**Independent Test**: Can be fully tested by triggering several distinct events in a row (multiple taps, a disconnect) and confirming all of them are visible in order in the widget, oldest to newest, by scrolling if needed.

**Acceptance Scenarios**:

1. **Given** several events have occurred in sequence, **When** the operator looks at the widget, **Then** each event appears as a separate, chronologically ordered entry.
2. **Given** the widget has accumulated many entries during a long session, **When** the operator scrolls up within the widget, **Then** older entries are still available to read.

---

### Edge Cases

- What happens when events occur faster than the operator can read them (e.g. rapid repeated taps)? The widget must keep up and continue showing the latest entries without the app slowing down or becoming unresponsive.
- What happens once the widget's history grows very large over a long session? Older entries are dropped once a reasonable cap is reached, so the app's memory usage doesn't grow without bound.
- What happens on screen rotation or other configuration changes? The widget's current history is not lost.
- What happens if the device doesn't allow the app to read broader Android system log output? The widget still shows everything the app itself produced; it does not fail or leave the operator without any information.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The app MUST display a log widget on the home/control screen, positioned directly below the direction control arrows.
- **FR-002**: The widget MUST show diagnostic messages the app produces during the current session, including at minimum: connection status changes and the outcome of each direction command (sent vs. dropped).
- **FR-003**: The widget MUST update automatically as new messages occur, with no manual refresh action required.
- **FR-004**: The widget MUST retain a history of recent messages (not only the single latest one) so the operator can review the order events occurred in.
- **FR-005**: The widget MUST remain scrollable when its message history exceeds the visible area, and MUST show the newest message by default.
- **FR-006**: The app MUST NOT crash, freeze, or block other interaction (e.g. tapping direction buttons) as a result of logging activity, even under rapid repeated events.
- **FR-007**: The widget's retained history MUST be bounded to a reasonable size so memory usage does not grow without limit over a long session.
- **FR-008**: The widget's content is scoped to the current app session only — it does not need to survive the app being closed and reopened.

### Key Entities

- **Log Entry**: A single diagnostic message shown in the widget, consisting of a short description of what happened (e.g. "Direction tapped: UP — sent", "Connection lost") and its relative order among other entries.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An operator testing on a physical phone with no computer attached can determine whether a given direction tap's command was actually sent or dropped, using only the on-screen widget.
- **SC-002**: A new event is reflected in the widget within 1 second of it occurring.
- **SC-003**: The widget continues to display and update correctly through at least 50 consecutive events in a session without the app slowing down or crashing.
- **SC-004**: The operator can read at least the last 20 events from the widget without leaving the home screen.

## Assumptions

- "Everything that is logged in the client" is scoped to this app's own diagnostic output (its structured log messages plus any console/stdout-style output it produces) — not the logs of other apps or the Android system as a whole. Regular apps cannot read other apps' or the system's log data on a stock, non-rooted device, and doing so would also be outside this app's single stated purpose of commanding the ESP32.
- The widget is read-only for this iteration — no clear, copy, or export action. Can be added later if it proves necessary (function over form; keep the first version minimal).
- "A small widget" means a compact, fixed-height scrollable panel — not a full-screen log viewer.
- The events described in FR-002 (connection status changes, command sent/dropped outcomes) are the initial required content; other existing app log messages may also be surfaced through the same widget where convenient, but are not separately required.
