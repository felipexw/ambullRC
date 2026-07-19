# Phase 0 Research: Home Screen UX Redesign

## Decision 1: Fixed dark palette, dynamic Material You color disabled

**Decision**: Replace `Color.kt`'s default Compose-starter colors with a fixed set of tokens
matching the design handoff (background `#151210`, surfaces `#1c1917`/`#211e1b`/`#2a2622`, accent
`#ff9a5a`, plus the connecting/connected/disconnected trio). `AmbullRCTheme` always builds its
`ColorScheme` from these fixed tokens — `dynamicColor` (Material You) is turned off and the
`darkTheme`/system-light-mode branch is dropped; the app renders the same dark theme regardless of
the system setting.

**Rationale**:
- The design is explicitly a single "full-bleed dark screen" with no light variant defined — this
  screen doesn't have a light-mode design to implement, so honoring `isSystemInDarkTheme()` would
  mean either fabricating an undesigned light palette (speculative, no requirement calls for it —
  Principle I) or leaving the redesign only half-applied on light-mode devices.
- Dynamic (Material You) color pulls from the device wallpaper, which would directly undermine
  SC-005 ("header, control area, and log panel share one consistent visual language") — the
  palette would vary per phone/wallpaper instead of matching the reviewed, coherent design tokens.
- Still zero new dependencies: this is `darkColorScheme(...)` with different literal `Color(...)`
  values, exactly the mechanism already in `Theme.kt`.

**Alternatives considered**:
- *Keep dynamic color, restyle only individual components*: rejected — status pill colors, accent
  usage, and surface layering are all defined relative to each other in the design; letting the
  base scheme still float with wallpaper would fight FR-001/FR-002/SC-005 rather than satisfy them.
- *Add a light theme too*: rejected per Principle I — not requested, no design exists for it, and
  this is a single-operator utility app, not a published app needing to honor OS-wide preferences.

## Decision 2: Cross D-pad layout built from `Row`/`Box`, not a grid library

**Decision**: Replace the current `Column`/`Row` of plain `IconButton`s with a manually composed
3×3 cross: one `Row` for the Up cell, one `Row` for Left/center-hub/Right, one `Row` for Down —
each interactive cell a fixed-size (76dp) `Box` with the existing triangle-rotation icon approach,
and the center hub a non-interactive decorative `Box`.

**Rationale**: Compose has no CSS-grid equivalent; `LazyVerticalGrid` is built for scrolling
collections, not a fixed 3×3 layout of five distinct elements (four buttons + one decorative
cell), so it would add ceremony without benefit. Three `Row`s of fixed-size `Box`es reproduces the
exact grid the design specifies with only foundation layout primitives already in use elsewhere in
this screen.

**Alternatives considered**: `LazyVerticalGrid(columns = GridCells.Fixed(3))` — rejected, no
scrolling/laziness is needed for 5 static cells and it also complicates keeping the center hub
purely decorative and the Up/Down cells spanning only the middle column.

## Decision 3: Custom drag-collapsible sheet, not Material3's modal bottom sheet

**Decision**: Build the log panel as a plain `Box`/`Column` positioned at the bottom of the
screen, with its height animated via `animateDpAsState` between a collapsed and expanded value,
and both tap-to-toggle and drag-to-resize implemented with Compose foundation's
`detectVerticalDragGestures` (via `Modifier.pointerInput`) on the drag-handle row. It is **not**
Material3's `ModalBottomSheet`/`BottomSheetScaffold`.

**Rationale**:
- FR-009 requires the sheet to be reachable via *either* a single tap *or* a drag, snapping open
  or closed based on which side of the midpoint the drag ends on (spec Edge Cases) — Material3's
  modal sheet is a scrim-backed overlay driven by its own internal swipe-to-dismiss state machine,
  which doesn't expose "toggle by tap on a collapsed strip" or "always partially visible,
  non-modal" behavior.
- The design's sheet is always present (never fully hidden, never dims the rest of the screen) —
  the opposite of "modal." A non-modal, always-visible, height-animated `Box` is both the more
  accurate match and the simpler mechanism (Principle I): no scrim, no dismiss-on-outside-tap
  logic, no sheet-state enum to reconcile with this feature's simpler two-state (collapsed/
  expanded) model.
- Everything used (`pointerInput`, `detectVerticalDragGestures`, `animateDpAsState`) is Compose
  foundation — no new dependency, consistent with Principle IV's "idiomatic Compose primitives
  only."

**Alternatives considered**:
- `BottomSheetScaffold` with a permanently-visible peek height — rejected: still modal-flavored
  (state machine has `Hidden`/`PartiallyExpanded`/`Expanded` states and scrim semantics not needed
  here) and doesn't natively support "tap the collapsed strip to toggle" without fighting its
  built-in gesture handling.
- A third-party bottom-sheet library — rejected outright per Principle IV (no new dependency for
  something Compose foundation already provides the primitives for).

## Decision 4: Expanded sheet height fills available space, not a fixed dp constant

**Decision**: Rather than hardcoding the design's literal `760px` expanded height, the sheet
expands to fill all space below the header (i.e., the same area the D-pad occupies), computed from
the actual measured layout (e.g., a `Box` wrapping the control area + sheet, with the sheet's
expanded target height = that `Box`'s height). The collapsed height stays a fixed, small constant
(40dp, matching the design) since that's a minimum touch-target-sized strip regardless of screen
size.

**Rationale**: `760px` was measured against the design tool's specific 412×892 preview frame.
Real devices vary in dp height (a compact phone can be well under 700dp tall in that axis), so a
literal fixed 760dp expanded height would either clip on smaller screens or leave an odd gap on
larger ones. "Fill the space below the header" reproduces the design's actual intent — quoting the
spec, "covers the entire D-pad area, leaving only the top app bar visible" — and adapts correctly
to any device, which is the more literal reading of the requirement than the specific pixel number.

**Alternatives considered**: Hardcode `760.dp` — rejected as described above (screen-size
fragility). Make it a percentage of screen height — rejected as an unnecessary extra concept when
"fill the remaining `Box`" already gives the exact right answer with a layout primitive already in
use (`fillMaxHeight`/`weight`).

## Decision 5: Log entries gain structure (timestamp, category, level)

**Decision**: Replace `DebugLog`'s `List<String>` with `List<LogEntry>`, where `LogEntry` carries
a timestamp, a `LogCategory` (`SENT` / `RECEIVED` / `CONNECTION` / `APP`, corresponding to the
design's `TX`/`RX`/`BLE`/`APP` tags), a `LogLevel` (`INFO`/`WARN`/`ERROR`), and the message text.
`DebugLog.add(category, level, message)` replaces `add(message)`; existing call sites in
`ConnectionViewModel`/`ControlViewModel` are updated to pass the right category/level instead of
pre-formatting everything into one string.

**Rationale**: Feature 004 deliberately kept log entries as plain strings because nothing at the
time needed more than message + arrival order (Principle I — no unused structure). This feature's
FR-012/SC-004 now explicitly require per-entry color-coding by category and by severity, which a
plain string cannot support without fragile re-parsing of the message text — so the added
structure is directly required, not speculative, and stays minimal (four fields, two small enums,
no class hierarchy).

**Note on the `RECEIVED` (`RX`) category**: the design's prototype simulates a two-way link and
logs a fake `"ESP32 ACK, motors idle"` on button release. This app's real `Esp32Connection` is
one-way — it only writes commands; it never reads a reply back (per the project constitution:
"Reading telemetry/state back from the ESP32 is out of scope"). So `LogCategory.RECEIVED` is
defined (for the color/tag mapping the design specifies) but no current call site produces one —
this is expected and correct: the type exists so entries *can* be tagged that way, not because
something must be tagged that way today. Inventing a fake ACK entry purely to exercise that color
would violate Principle III (no capability the app doesn't actually have) and Principle I
(fabricated data with no source of truth).

**Alternatives considered**: Keep `List<String>` and infer category/level by parsing message
prefixes at render time — rejected, more fragile and more code than typing the entry at the
source, and breaks the moment a message's wording changes.

## Decision 6: Log panel's open/closed and drag state stay local Compose state

**Decision**: Whether the log panel is expanded, and the live height while mid-drag, are held as
local `remember { mutableStateOf(...) }` state inside `DebugLogPanel` itself — not lifted into a
ViewModel or `MainActivity`.

**Rationale**: This mirrors an existing precedent already in the codebase — `ControlScreen`'s
`DirectionButton` keeps its own `MutableInteractionSource`/pressed-state locally, because it's
ephemeral presentation state with no business meaning, not something another part of the app needs
to read or that needs to survive the Composable's own lifecycle. The log panel's open/closed state
is the same kind of thing: purely how the existing `entries` are currently being displayed, not
data. Constitution Principle II ("Composables take state + callbacks") is about the *business*
state a Composable renders (connection state, log entries, press outcomes) coming from a
ViewModel — it doesn't require promoting every UI-only interaction detail upward, and doing so
here would add a ViewModel dependency/plumbing purely to shuttle a boolean, which Principle I
weighs against.

**Alternatives considered**: Track `logSheetOpen` in a ViewModel (as the design's own internal
prototype state does) — rejected as unnecessary indirection for state nothing outside the
Composable needs.
