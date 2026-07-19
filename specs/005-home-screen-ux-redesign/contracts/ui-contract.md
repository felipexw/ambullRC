# UI / ViewModel Contract: Home Screen UX Redesign

This is an Android app with no external/network API. The contract it exposes is the UI surface
(Composables + ViewModel + log data model) that user actions and tests bind to. This contract only
covers what **changes** in this feature — `ConnectionState`, `Direction`, `Esp32Connection`,
`ConnectionViewModel`, and `ControlViewModel`'s public methods are unchanged (see features
002/003's contracts).

## `LogEntry` / `LogCategory` / `LogLevel` (new, `viewmodel` package)

```kotlin
enum class LogCategory(val tag: String) { SENT("TX"), RECEIVED("RX"), CONNECTION("BLE"), APP("APP") }
enum class LogLevel { INFO, WARN, ERROR }

data class LogEntry(
    val timestamp: LocalTime,
    val category: LogCategory,
    val level: LogLevel,
    val message: String,
)
```

- Immutable value type. `LogCategory.tag` is exactly the 2-3 letter string rendered in the log row.
- See [data-model.md](../data-model.md) for the category/level assigned at each call site.

## `DebugLog` (modified)

```kotlin
class DebugLog {
    val entries: StateFlow<List<LogEntry>>
    fun add(category: LogCategory, level: LogLevel = LogLevel.INFO, message: String)
}
```

- **Contract**: identical append/cap semantics to feature 004 (`entries` oldest-first, capped at
  50, dropping the oldest first) — only the element type and `add()`'s parameters change.
- **Breaking change**: `add(message: String)` no longer exists; every existing call site
  (`ConnectionViewModel`, `ControlViewModel`) must be updated to pass a `LogCategory` (see
  data-model.md's table for the correct one per call site).

## `ConnectionStatusBar` (Composable, modified)

```kotlin
@Composable
fun ConnectionStatusBar(
    state: ConnectionState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
)
```

- **Signature unchanged** — this is a pure visual redesign of an existing contract.
- **Renders**:
  - Device name (bonded name when `Connected`, "No device" otherwise), truncating with ellipsis.
  - A status pill (background/foreground/dot color + label) per the mapping in data-model.md;
    pulses only while `Connecting`.
  - A Retry button, visible only when the state maps to the "disconnected" bucket (`Idle` or
    `Failed`); calls `onRetry()` when tapped.
- **Guarantees** (asserted by instrumented tests):
  - Exactly one of the three status colors/labels is shown per state (FR-001).
  - The dot animates if and only if `state is ConnectionState.Connecting` (FR-002).
  - Retry is present if and only if `state is ConnectionState.Idle || state is ConnectionState.Failed` (FR-004).

## `ControlScreen` / `DirectionButton` (Composable, modified)

```kotlin
@Composable
fun ControlScreen(
    viewModel: ControlViewModel,
    connected: Boolean,
    modifier: Modifier = Modifier,
)
```

- **Signature adds `connected: Boolean`** (previously the screen had no notion of connection
  state — needed for FR-007/FR-008, the disabled-controls and hint-text behavior). The caller
  (`MainActivity`) passes `connectionViewModel.state.collectAsState().value == ConnectionState.Connected`.
- **Renders**: the four direction controls in a cross layout around a decorative center hub (see
  research.md Decision 2), each showing a pressed-state highlight while held and a dimmed
  disabled appearance when `connected == false`; a hint line below reflecting `connected`.
- **Behavior unchanged**: press/release still call `viewModel.onDirectionPressed`/
  `onDirectionReleased` exactly as before — this feature does not touch command-sending logic,
  only whether input is accepted (`connected == false` ⇒ presses are ignored, matching FR-007).
- **Guarantees** (asserted by instrumented tests):
  - Each control shows its distinct pressed appearance only while held (FR-006).
  - When `connected == false`, all four controls are dimmed and pressing them does not invoke
    `onDirectionPressed` (FR-007).
  - The hint text matches `connected` (FR-008).

## `DebugLogPanel` (Composable, modified)

```kotlin
@Composable
fun DebugLogPanel(
    entries: List<LogEntry>,
    modifier: Modifier = Modifier,
)
```

- **Signature**: `entries` type changes from `List<String>` to `List<LogEntry>`; no other
  parameters — open/closed and drag state are internal (research.md Decision 6).
- **Renders**:
  - Collapsed (default): a fixed-height (40dp) strip with a drag handle and a live
    `"LOGS · <count>"` label.
  - Expanded: a header ("Device Logs" + `"<count> lines"`), and a scrollable list of entries, each
    showing timestamp, `category.tag` (colored by category), and message (colored by level).
  - Auto-scrolls to the newest entry while expanded and a new entry arrives (FR-013).
- **Interaction**:
  - Tapping the collapsed strip toggles to expanded, and vice versa (FR-009).
  - Dragging the handle resizes the sheet live; on release, snaps fully open/closed based on
    which side of the midpoint the drag ended on; a drag under a small threshold (~10dp) is
    treated as a tap instead (FR-009, spec Edge Cases).
  - Collapsed↔expanded height changes animate (~200ms), whether triggered by tap or drag-release
    (FR-010).
- **Guarantees** (asserted by instrumented tests):
  - Collapsed state shows no individual entry rows, only the count (FR-011).
  - Expanded state's newest entry is scrolled into view after new entries arrive (FR-013).
  - Entries with different `category`/`level` values render with visibly different colors
    (FR-012).

## `AmbullRCTheme` (modified)

```kotlin
@Composable
fun AmbullRCTheme(content: @Composable () -> Unit)
```

- **Signature**: `darkTheme`/`dynamicColor` parameters removed — the theme is now always the
  fixed dark palette from `Color.kt` (research.md Decision 1). Callers (just `MainActivity`)
  simply drop those arguments; there is no behavior for them to select anymore.
