# Feature Specification: Horn Sound Command (Played by the ESP32)

**Feature Branch**: `008-a2dp-audio-streaming`

**Created**: 2026-09-15

**Status**: Draft

**Input**: User description: "Implement A2DP audio integration between the app and the esp 32. the
audio should be sent from the mobile to the esp32 always. also, in face of conflict between motors
(servo and dc) commands with audio streaming, it should prioritize the motor control" — refined
through two rounds of feedback into its final shape: (1) a feasibility check found a normal Android
app cannot control system-wide Bluetooth audio routing, so the feature became a single built-in
horn/siren sound effect; (2) a further correction moved the sound itself onto the ESP32 — the app's
only job is to trigger it and know when it's done, entirely over the existing RFCOMM/SPP command
channel. No Bluetooth audio profile (A2DP or otherwise) is used by this feature.

## Clarifications

### Session 2026-09-15

- Q: How should the app determine the horn has finished playing, so the control can be tapped
  again? → A: Don't wait for any ESP32/MCU response at all. Use a fixed local timer of 1500ms
  measured from the moment the trigger was sent; once that threshold elapses, re-enable the
  control automatically, regardless of what the ESP32 does or reports.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Sound the horn without sacrificing control (Priority: P1)

While connected to the vehicle, the operator taps a horn/siren control in the app. The ESP32 plays
its own built-in horn sound; the app does not wait to hear back from the ESP32 about it — it simply
keeps the control disabled for a fixed 1500ms after the tap, then re-enables it. Throughout, the
operator can still steer and drive at the same responsiveness as always — sounding the horn never
makes driving feel laggy.

**Why this priority**: This is the entire value of the feature. A horn that came at the cost of
control responsiveness would make the vehicle unsafe/unusable to drive, which conflicts with the
app's core purpose. This story must work before anything else here matters.

**Independent Test**: With the vehicle connected, tap the horn control; confirm the ESP32's horn
sounds and the app's control reflects "sounding" for 1500ms, then automatically returns to normal —
with no dependency on any response from the ESP32. While it's in that 1500ms window, send a rapid
burst of direction commands (as in normal driving) and confirm the vehicle responds exactly as
promptly as always.

**Acceptance Scenarios**:

1. **Given** the phone is connected to the ESP32, **When** the operator taps the horn control,
   **Then** a single trigger command is sent and the ESP32 sounds its horn.
2. **Given** the horn was triggered, **When** 1500ms have elapsed since that trigger was sent,
   **Then** the horn control automatically returns to its normal, tappable state, regardless of
   whether or what the ESP32 has reported.
3. **Given** the operator is holding a direction button, **When** they also tap the horn control,
   **Then** the direction command keeps arriving at its normal cadence with no added delay,
   regardless of the horn trigger happening at the same time.

---

### User Story 2 - A repeat tap within 1500ms of the last one does nothing (Priority: P1)

If the operator taps the horn control again before 1500ms have elapsed since the previous trigger,
the app does not send another trigger — it waits until that fixed window has fully elapsed before
a new tap can sound the horn again.

**Why this priority**: Without this, rapid or accidental repeat taps could pile up trigger commands
the ESP32 was never asked to queue or overlap, producing an undefined, likely broken sound.
Equally core to a horn command actually being usable and predictable as Story 1.

**Independent Test**: Tap the horn control, then tap it again immediately (well within 1500ms).
Confirm only one trigger command was ever sent, and that a tap sent after the 1500ms window has
elapsed does trigger a new sound.

**Acceptance Scenarios**:

1. **Given** fewer than 1500ms have elapsed since the last trigger, **When** the operator taps the
   horn control again, **Then** no additional trigger command is sent.
2. **Given** 1500ms or more have elapsed since the last trigger, **When** the operator taps the
   horn control, **Then** a new trigger command is sent and the horn sounds again.

---

### User Story 3 - Clear feedback on whether the horn can be used (Priority: P3)

The operator can always tell, at a glance, whether the horn control is currently usable
(connected and not already sounding) or not (disconnected, or mid-sound).

**Why this priority**: Per the app's UX principle, every control's real state must be visible.
Hardening/polish once Stories 1 and 2 establish the actual behavior.

**Independent Test**: Observe the horn control while disconnected, while connected and idle, and
while sounding; confirm each is visually distinct.

**Acceptance Scenarios**:

1. **Given** the app is not connected to the ESP32, **When** the operator looks at the horn
   control, **Then** it is clearly shown as unavailable.
2. **Given** the app is connected and the horn is not currently sounding, **When** the operator
   looks at the horn control, **Then** it is clearly shown as ready to tap.
3. **Given** the horn is currently sounding, **When** the operator looks at the horn control,
   **Then** it is clearly shown as sounding (distinct from both unavailable and ready).

---

### Edge Cases

- What happens when a direction command needs to go out at the same moment the horn's trigger
  does? The direction command MUST NOT be delayed — both travel over the same simple command
  channel already used for direction/lights commands (no separate audio data involved at all).
- What happens if the horn's trigger command fails to send (e.g., not connected)? The app MUST NOT
  behave as if the horn is now sounding — it must remain in its normal (unavailable/ready) state,
  and the 1500ms window MUST NOT start for a trigger that was never actually sent.
- What happens if the drive-command connection to the ESP32 drops while the horn is within its
  1500ms window? The app MUST treat the horn as no longer sounding and show it as unavailable,
  consistent with the rest of the app's controls going unavailable on disconnect.
- What happens if the ESP32 actually takes longer than 1500ms to finish playing the sound (or
  never plays it at all, e.g. a malfunction)? The app re-enables the control at 1500ms regardless —
  it never waits for or checks the ESP32's actual state. This is an accepted tradeoff (see
  Assumptions): a possible mismatch between what the button shows and what's physically still
  audible is preferred over any dependency on an ESP32 response.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The app MUST let the operator trigger the ESP32's built-in horn/siren sound by
  tapping a control in the app, while connected.
- **FR-002**: The app MUST send exactly one trigger command per usable tap — no audio data of any
  kind is generated, held, or transmitted by the app itself; the ESP32 is solely responsible for
  producing and playing the sound.
- **FR-003**: The app MUST track whether the horn is currently sounding using a fixed local timer
  of 1500ms measured from the moment the trigger was successfully sent — the app MUST NOT wait
  for, depend on, or otherwise use any ESP32-reported completion signal to make this determination.
- **FR-004**: While fewer than 1500ms have elapsed since the last successfully sent trigger,
  tapping the control again MUST NOT send another trigger command.
- **FR-005**: Once 1500ms have elapsed since the last successfully sent trigger, the control MUST
  become tappable again automatically, regardless of any response (or lack of one) from the ESP32.
- **FR-006**: The app MUST display the horn's current status to the operator (at minimum:
  unavailable, ready, sounding).
- **FR-007**: Sending the horn's trigger MUST NOT delay, drop, or otherwise affect the delivery of
  steering/throttle commands, and vice versa.
- **FR-008**: If the drive-command connection to the ESP32 is lost, the horn MUST be treated as
  unavailable, consistent with the rest of the app's connection-gated controls.

### Key Entities

- **Horn Playback Status**: The current status of the horn as observed by the operator (e.g.,
  unavailable / ready / sounding), tracked independently of — but alongside — the existing
  drive-command connection status, and driven entirely by a fixed 1500ms local timer started when
  a trigger is successfully sent — never by any signal or response from the ESP32.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A tap on the horn control results in the ESP32 sounding the horn with no perceptible
  delay from the tap itself.
- **SC-002**: Driving commands sent while the horn is sounding are indistinguishable, from the
  operator's perspective, in responsiveness from driving commands sent with the horn idle.
- **SC-003**: Across sustained simultaneous use (driving + repeatedly sounding the horn), no
  steering/throttle command is ever delayed, dropped, or blocked by horn-related traffic.
- **SC-004**: Across any number of repeat taps within a given 1500ms window, exactly one trigger
  command reaches the ESP32 — never more than one in flight at a time.
- **SC-005**: Operators can always tell, within the app, whether the horn is unavailable, ready, or
  sounding.

## Assumptions

- **Why the sound lives on the ESP32, not the app**: an initial "app streams audio to the ESP32"
  design was found infeasible for a normal Android app (system-wide Bluetooth audio routing isn't
  something a third-party app can control), and a follow-up correction then established that the
  ESP32 already will own the sound entirely — the app's role is reduced to sending a short trigger
  over the plain-text command channel that already carries steering/throttle/lights commands. No
  Bluetooth audio profile, no audio codec, and no audio data of any kind are part of this feature.
- The horn/siren sound itself (its audio content and duration) is entirely the ESP32's concern;
  this app-side feature does not implement or ship any ESP32 firmware or sound asset.
- **Why a fixed local timer instead of an ESP32-reported completion signal** (Clarifications): the
  app deliberately does not read back any horn-related state from the ESP32 at all — no
  "playing"/"done" signal is expected or parsed. Re-enabling the control is purely a local,
  fixed-duration timeout (1500ms) started the moment the trigger is successfully sent. This trades
  perfect accuracy (the button could re-enable slightly before or after the real sound actually
  ends, if the ESP32's actual duration differs from 1500ms) for simplicity — no inbound protocol
  or read-back parsing is needed for this feature at all. The 1500ms value is a fixed constant, not
  configurable, consistent with how other timings in this app (e.g. the 100ms directional-repeat
  interval) are hardcoded rather than tunable.
- If the underlying Bluetooth connection to the ESP32 is lost entirely, the horn's status resets to
  unavailable along with the rest of the app's connection-gated controls, regardless of where the
  1500ms window was at.
- No new user accounts, cloud services, custom sound selection/upload, or media-library features
  are introduced; this stays within the app's existing single-device, single-purpose scope.
