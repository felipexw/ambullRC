# Phase 0 Research: In-App Log Viewer Widget

## Decision 1: Capture mechanism — explicit first-party log sink, not real Logcat/stdout capture

**Decision**: The widget is fed by a small, explicit, in-process log sink (`DebugLog`) that
`ConnectionViewModel` and `ControlViewModel` write human-readable strings to at the exact points
already required by FR-002 (connection state transitions, direction-tap send/drop outcomes). It does
**not** attempt to read the real Android Logcat stream or intercept `System.out`/`System.err`.

**Rationale**:
- Modern Android restricts a regular (non-system, non-rooted) app to reading only its own process's
  Logcat entries, and even that requires shelling out to the `logcat` binary via `ProcessBuilder` —
  fragile across OEM skins/Android versions, requires managing a long-lived child process and a
  reader thread/coroutine, and adds real complexity for zero benefit here, since this app currently
  emits a grand total of one `Log.d` call (`AndroidDirectionLogger`).
- The spec's own Assumptions section already scopes "everything logged" to this app's own diagnostic
  output, not other apps'/system-wide logs — an explicit sink satisfies that scope exactly.
- Directly satisfies Constitution Principle I (YAGNI): the two events FR-002 actually requires
  (connection state, tap outcome) already flow through `ConnectionViewModel`/`ControlViewModel`; the
  simplest solution is to write them to a shared buffer at the source, not reconstruct them by parsing
  Logcat text after the fact.
- Keeps the feature entirely within the existing MVVM seam pattern: ViewModels write, a stateless
  Composable reads/renders — no new Android-framework-bound seam (interface + fake) is needed because
  `DebugLog` itself has no Android/hardware dependency, exactly like `ConnectionState`.

**Alternatives considered**:
- *Shell out to `logcat -d --pid=<own pid>`*: would technically also capture `System.out`/`System.err`
  (Android's runtime redirects those to Logcat by default), literally matching "std.out" in the user's
  wording. Rejected: permission/OEM fragility, process-lifecycle complexity, and no current code path
  produces meaningful `System.out` output anyway — there is nothing to gain from this project's actual
  present logging usage.
- *Custom `Timber`-style logging library*: rejected as a new dependency for a single-screen debug
  widget (Principle I; "adopt a new dependency only when the task genuinely cannot be done reasonably
  without it").

## Decision 2: Buffer capacity

**Decision**: Cap the ring buffer at 50 entries (oldest dropped first), matching SC-003's "at least 50
consecutive events" requirement — behavior stays correct at that volume, and 50 short lines is already
several screens of scrollable history, comfortably covering SC-004 ("at least the last 20 events").

**Rationale**: Bounds memory per FR-007 with the simplest possible mechanism (`list.takeLast(50)` on
each append) — no LRU library, no persistence, no size-based (byte) accounting needed at this scale.

**Alternatives considered**: Unbounded list — rejected, violates FR-007 directly for long sessions.
Configurable capacity — rejected per Principle I, no requirement calls for it.

## Decision 3: Shared instance ownership

**Decision**: `MainActivity` constructs a single `DebugLog` instance and passes it into both the
`ConnectionViewModel` and `ControlViewModel` factories, mirroring the existing pattern for the shared
`Esp32Connection` instance (feature 003).

**Rationale**: Both ViewModels need to append to the *same* buffer so entries from both sources appear
interleaved in chronological order in one widget. Consistent with the app's existing
constructor-injection style — no DI framework needed (Principle I).

## Decision 4: Placement and rendering

**Decision**: `MainActivity` renders a new stateless `DebugLogPanel` Composable directly below the
existing `ControlScreen` call inside its `Column`, collecting `DebugLog.entries` as Compose state. The
panel is a fixed-height `LazyColumn` that auto-scrolls to the newest entry on each update.

**Rationale**: Satisfies FR-001 (position) and FR-005 (scrollable, newest visible by default) with the
same Compose primitives already used for `ConnectionStatusBar`/`ControlScreen` — no new UI library.
