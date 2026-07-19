# Feature Specification: Home UI & Branding Refresh

**Feature Branch**: `006-home-ui-branding-refresh`

**Created**: 2026-07-19

**Status**: Draft

**Input**: User description: "Let's improve the app UX in a few steps: improve the header home page (1) by
adding an icon in the top left; increase the size of the control buttons, so that they fill the
whole space - without transpassing the other widget spaces, like bottom and header; also, remove
"hold a direction to drive" (2); replace the app icon (3); create a splash screen following the
orange (verify exactly) with the app icon (4). It's attached here the assets that I generated with
Claude Design."

Four supplied image assets (stored alongside this spec in `assets/`):
- `icon-background-432.png` — solid dark background layer (`#151210`) for the adaptive app icon.
- `icon-foreground-432.png` — car + gamepad line-art in orange (`#FF9A5A`) for the adaptive app icon's foreground layer.
- `icon-flat-512.png` — flattened combined app icon (dark rounded-square background + orange car/gamepad mark) for the legacy/round launcher icon.
- `splash-1080x2400.png` — full splash screen composition: dark background (`#151210`), the orange car/gamepad mark centered, "RC CAR" wordmark in orange, and "Bluetooth Remote Control" subtitle in muted gray, all in the upper-middle portion of a tall canvas.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Controls fill the available space (Priority: P1)

As the driver, when I open the app I want the directional buttons to be as large as possible so
they're easy to hit accurately while glancing at the car, not the phone.

**Why this priority**: The D-pad is the primary interaction surface of the whole app (its single
purpose is sending drive commands); undersized buttons directly hurt the core task every session.

**Independent Test**: Launch the app on a device/emulator, observe the control area between the
header and the log panel — the four directional buttons and center hub scale up to fill that
region's width/height while every button stays fully visible and none overlaps the header or the
log panel.

**Acceptance Scenarios**:

1. **Given** the home screen is showing on a phone in portrait, **When** the screen renders,
   **Then** each directional button is larger than the current fixed 76dp cell and the cross layout
   expands to use the available width and height of the control region.
2. **Given** the control region has a fixed height between the header and the log panel,
   **When** the buttons scale up, **Then** no button or the center hub is clipped, overlaps the
   header bar, or overlaps the log panel — the whole D-pad remains inside its region with existing
   gaps preserved proportionally.
3. **Given** the device is disconnected, **When** the enlarged buttons render, **Then** the existing
   dimmed/disabled visual treatment (reduced opacity, no press feedback) still applies at the new size.

---

### User Story 2 - Simplified control hint (Priority: P2)

As the driver, I no longer see the "Hold a direction to drive" caption once connected, since the
larger buttons make the interaction self-evident and the caption only added visual clutter.

**Why this priority**: Small copy-and-layout cleanup; independent of the button-sizing work and has
no functional risk, but should ship alongside the redesigned control area since it touches the same
composable.

**Independent Test**: Connect to the ESP32 (or simulate `connected = true`) and confirm no hint text
renders below the D-pad; disconnect and confirm the existing "Waiting for connection to enable
controls" message still renders (unchanged).

**Acceptance Scenarios**:

1. **Given** the app is connected, **When** the home screen renders, **Then** no "Hold a direction to
   drive" text appears anywhere in the control region.
2. **Given** the app is disconnected, **When** the home screen renders, **Then** the
   "Waiting for connection to enable controls" hint still appears exactly as before (this message is
   not part of the removal — only the connected-state hint is removed).

---

### User Story 3 - Branded header icon (Priority: P2)

As the driver, I see the app's car/gamepad mark in the top-left of the header bar, next to the
device name, so the app feels branded and polished rather than generic.

**Why this priority**: Purely visual addition to the existing header; independently testable and
shippable without touching connection logic.

**Independent Test**: Launch the app and visually confirm a small icon renders at the header's
top-left, to the left of the device name text, in any connection state.

**Acceptance Scenarios**:

1. **Given** the home screen is showing, **When** the header renders in any connection state (Idle,
   Connecting, Connected, Failed), **Then** a small icon appears at the leading (top-left) edge of the
   header, before the device name text.
2. **Given** the header row has a fixed height, **When** the icon renders, **Then** it does not force
   the header to grow taller and does not push the status pill/Retry button off-screen or wrap them
   awkwardly on a standard phone width.

---

### User Story 4 - New app icon and matching splash screen (Priority: P1)

As anyone who installs or launches the app, I see the new orange car/gamepad app icon on the home
screen/app drawer, and a splash screen with the exact same dark background and orange mark appears
briefly on cold launch before the home screen loads — so first impression and app-switcher branding
match the new design direction.

**Why this priority**: Branding-visible on every install and every cold launch; highest visibility
of the four asks, and the two assets (icon, splash) are a matched pair from the same design so they
must ship together to look intentional rather than mismatched.

**Independent Test**: Build and install the app; confirm the launcher icon (home screen, app
drawer, recents/app-switcher thumbnail) shows the new orange car/gamepad mark; force-stop the app and
cold-launch it, confirming a splash screen with the same dark background and orange mark/wordmark
appears before the home screen UI is shown.

**Acceptance Scenarios**:

1. **Given** the app is installed, **When** the user looks at the home screen/app drawer icon,
   **Then** it shows the new adaptive icon (dark background + orange car/gamepad line art), replacing
   the current default Android-robot placeholder icon.
2. **Given** the app is cold-launched (not already in memory), **When** the splash screen shows,
   **Then** its background color and icon mark visually match the supplied splash asset (same dark
   background, same orange car/gamepad mark, centered in the same relative position), and it
   transitions to the home screen automatically once the app is ready — no user interaction required
   to dismiss it.
3. **Given** the app is already running and resumed from the background (not a cold start),
   **When** the user returns to it, **Then** no splash screen replay is required (standard Android
   splash-screen behavior — cold start only).

### Edge Cases

- Very small/short phone screens: the enlarged control region must still respect the fixed header
  and log-panel heights and not force those regions to shrink or get pushed off-screen — the D-pad
  scales within whatever space remains, even if that means smaller-than-ideal buttons on small
  screens.
- Landscape orientation: out of scope for this feature (existing screens are portrait-only; no new
  landscape-specific layout is introduced or required to be exercised).
- Header icon at very narrow widths: if the device name is long, the existing ellipsis truncation on
  the device-name text (already implemented) continues to protect the layout from overflow; the new
  icon must not be a new source of overflow.
- Splash screen on Android versions before the platform splash-screen API existed: not applicable —
  the app's minimum supported Android version already meets the platform's native splash-screen
  requirement, so no fallback/compat path is needed.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The control (D-pad) region MUST size its directional buttons and center hub to fill
  the available width and height of the space between the header and the log panel, rather than
  using a fixed button size, while preserving the existing cross layout and relative gaps between
  cells.
- **FR-002**: The control region's expansion MUST NOT change the height allotted to the header or
  the log panel, and MUST NOT cause any button, the center hub, or their visual press/disabled
  states to be clipped or drawn outside the control region's bounds.
- **FR-003**: The existing per-button disabled (dimmed, non-interactive) and pressed (accent
  background/glow) visual states MUST continue to work unchanged at the new, larger button size.
- **FR-004**: The "Hold a direction to drive" hint text MUST be removed from the control screen for
  the connected state. The disconnected-state hint ("Waiting for connection to enable controls")
  MUST remain unchanged.
- **FR-005**: The header MUST display a small app/brand icon at its leading (top-left) edge, to the
  left of the device name, visible in every connection state, without increasing the header's height
  or displacing/wrapping the status pill or Retry button on standard phone widths.
- **FR-006**: The app's launcher icon (adaptive icon background + foreground, and any legacy/round
  variants) MUST be replaced with the supplied car/gamepad design (dark background layer, orange
  foreground line-art), replacing the current default placeholder icon across all generated
  mipmap densities.
- **FR-007**: The app MUST show a splash screen on cold launch whose background color and centered
  icon mark match the supplied splash asset exactly — same dark background hex value and same
  orange mark — using the platform's standard splash-screen mechanism (no custom splash Activity or
  bespoke rendering layer).
- **FR-008**: The splash screen MUST dismiss automatically and transition to the home screen once
  the app has finished its normal startup work, with no user interaction required to dismiss it, and
  MUST NOT replay on warm resumes (only on cold start), per standard platform splash-screen behavior.
- **FR-009**: The exact colors used for the new header icon, launcher icon, and splash screen MUST
  reuse the project's existing dark-background and orange-accent design tokens rather than
  introducing new hardcoded color values, since those tokens already match the supplied assets'
  colors precisely (dark `#151210`, orange `#FF9A5A`).

### Key Entities

- **App icon**: the adaptive launcher icon (background + foreground drawable layers) and the
  flattened legacy icon shown on the home screen, in the app drawer, and in recents.
- **Splash screen**: the transient branded screen the OS shows during cold app startup, defined by a
  background color and a centered icon/mark.
- **Header icon**: a small, non-interactive brand mark added to the existing connection-status
  header row.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On a standard phone screen, the directional buttons occupy at least 90% of the
  control region's available width (cross layout width) versus the current fixed-size layout, with
  zero visual overlap or clipping against the header or log panel.
- **SC-002**: 100% of screens/states that previously showed "Hold a direction to drive" no longer
  show it; the disconnected-state hint is unaffected in all states.
- **SC-003**: The header displays the brand icon in 100% of connection states without any observed
  layout wrapping or overflow on common phone widths (360dp–430dp).
- **SC-004**: The installed app's launcher icon and cold-launch splash screen are visually
  indistinguishable (same background color, same mark, same relative position) from the supplied
  design assets when compared side by side.
- **SC-005**: Cold launch shows the splash screen and reaches the interactive home screen with no
  manual dismissal step, on every cold start.

## Assumptions

- The four supplied PNGs (`icon-background-432.png`, `icon-foreground-432.png`,
  `icon-flat-512.png`, `splash-1080x2400.png`) are final-approved artwork, not placeholders — no
  further design iteration is expected as part of this feature.
- "The orange (verify exactly)" refers to matching the supplied assets' exact color values, which
  were sampled and confirmed to already equal the app's existing `Background` (`#151210`) and
  `Accent` (`#FF9A5A`) design tokens (see `app/src/main/java/com/example/ambullrc/ui/theme/Color.kt`) —
  no new color tokens are introduced.
- The splash screen shows only the mark (no "RC CAR" wordmark/subtitle text) mirroring the
  platform-standard splash-screen pattern (icon + background color only); the wordmark/subtitle
  present in the full splash mock are treated as the mock's own presentation, not a requirement to
  render literal text on the platform splash surface, since the platform splash API's supported
  surface is an icon + background color, not arbitrary layout.
- The device name / status pill / Retry button layout and behavior in the header are otherwise
  unchanged — only a leading icon is added.
- No changes to Bluetooth connection logic, command protocol, or the log panel are in scope; this is
  a UI/branding-only feature.
- Landscape orientation and tablet form factors remain out of scope, consistent with the app's
  existing phone-portrait-only design.
