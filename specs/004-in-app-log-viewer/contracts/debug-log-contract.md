# Contract: DebugLog and its ViewModel/UI integration

This app has no network/API surface; the "contract" here is the internal Kotlin interface between the
new `DebugLog` buffer, the two ViewModels that write to it, and the Composable that reads it.

## `DebugLog` (new — `viewmodel/DebugLog.kt`)

```kotlin
class DebugLog {
    val entries: StateFlow<List<String>>   // oldest first, capped at 50
    fun add(message: String)               // appends, then truncates to most recent 50
}
```

**Guarantees**:
- `add` never throws and never blocks (pure in-memory list update) — safe to call from any ViewModel
  coroutine without a dispatcher switch, satisfying FR-006 (must not block UI interaction).
- `entries` always reflects the most recent `add` calls in call order, oldest first, length ≤ 50.

## `ConnectionViewModel` (modified — `viewmodel/ConnectionViewModel.kt`)

```kotlin
class ConnectionViewModel(
    private val connection: Esp32Connection,
    private val debugLog: DebugLog = DebugLog(),
    private val connectTimeoutMillis: Long = 12_000L,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel()
```

**New guarantee**: every `_state` transition (`Connecting`, `Connected`, `Failed`, and the
drop-detected path in `monitorForDrop`) is paired with exactly one `debugLog.add(...)` call describing
that transition, in the same order the state changes are observed via `state`.

## `ControlViewModel` (modified — `viewmodel/ControlViewModel.kt`)

```kotlin
class ControlViewModel(
    private val connection: Esp32Connection,
    private val logger: DirectionLogger = AndroidDirectionLogger(),
    private val debugLog: DebugLog = DebugLog(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel()
```

**New guarantee**: every call to `onDirectionTapped` results in exactly one `debugLog.add(...)` call
once the send attempt completes, reporting whether that direction's command was sent or dropped.
Existing `logger.log(direction)` behavior (feature 001/003) is unchanged.

## `DebugLogPanel` (new — `ui/DebugLogPanel.kt`)

```kotlin
@Composable
fun DebugLogPanel(entries: List<String>, modifier: Modifier = Modifier)
```

**Guarantees**: stateless — renders exactly the `entries` it's given, newest at the bottom and
auto-scrolled into view; no logic, no side effects, no reference to `DebugLog`/ViewModels directly
(consistent with `ConnectionStatusBar`/`ControlScreen`'s existing stateless-Composable pattern).

## `MainActivity` wiring (modified)

- Constructs one `DebugLog` instance, passed into both `connectionViewModel` and `controlViewModel`
  factories.
- Renders `DebugLogPanel(entries = debugLog.entries.collectAsState().value)` immediately below the
  existing `ControlScreen(...)` call inside the root `Column`.

## Out of scope for this feature

- No clear/copy/export action on the widget (spec Assumptions).
- No capture of real Android Logcat or `System.out`/`System.err` (research.md Decision 1).
- No persistence across app restarts (FR-008).
