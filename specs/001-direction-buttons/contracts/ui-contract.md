# UI / ViewModel Contract: Direction Buttons Screen

This is an Android app with no external/network API. The contract it exposes is the UI
surface (Composable + ViewModel + logging seam) that user actions and tests bind to.

## `Direction` (model)

```
enum class Direction { UP, DOWN, LEFT, RIGHT }
```

Closed set of four values. See [data-model.md](../data-model.md) for the description/tag map.

## `DirectionLogger` (seam)

```
fun interface DirectionLogger {
    fun log(direction: Direction)
}
```

- **Contract**: `log(direction)` records exactly one occurrence of `direction`. It must
  not drop, batch, or reorder calls — one invocation = one recorded occurrence.
- **Production implementation**: writes one line per call to the Android system log
  (`android.util.Log.d`), tag and message identifying the direction.
- **Test implementation**: a fake that appends each received `direction` to an in-memory
  list for assertions.

## `ControlViewModel`

```
class ControlViewModel(
    private val logger: DirectionLogger = <Android Log-backed default>
) {
    fun onDirectionTapped(direction: Direction)
}
```

- **`onDirectionTapped(direction)`**: calls `logger.log(direction)` exactly once. No other
  side effects, no state mutation, no return value.
- **Guarantees** (asserted by unit tests):
  - Calling with `UP`/`DOWN`/`LEFT`/`RIGHT` logs that same value (FR-004).
  - N calls produce N logged records in call order (FR-005).
  - A single call logs exactly one record and never a different/extra direction (FR-006).
- **Default constructor arg** lets the Composable instantiate it with no wiring; tests
  pass a fake `DirectionLogger`.

## `ControlScreen` (Composable)

```
@Composable
fun ControlScreen(
    modifier: Modifier = Modifier,
    viewModel: ControlViewModel = remember { ControlViewModel() }
)
```

- **Renders** exactly four `IconButton`s, one per `Direction`, each with:
  - the matching Material icon: `KeyboardArrowUp` / `KeyboardArrowDown` /
    `KeyboardArrowLeft` / `KeyboardArrowRight`;
  - the content description and test tag from the data-model table
    (`"Up"`/`btn_up`, `"Down"`/`btn_down`, `"Left"`/`btn_left`, `"Right"`/`btn_right`).
- **On tap** of a button, calls `viewModel.onDirectionTapped(<that Direction>)` and does
  nothing else. The Composable contains no logging or business logic (Principle II).
- **Layout**: functional arrangement sufficient to identify and tap each of the four
  directions (e.g., a simple cross/column). No specific visual styling is contracted
  (Principle IV).

## Test selectors (stable identifiers)

| Direction | Content description | Test tag    | Material icon        |
|-----------|---------------------|-------------|----------------------|
| UP        | `Up`                | `btn_up`    | `KeyboardArrowUp`    |
| DOWN      | `Down`              | `btn_down`  | `KeyboardArrowDown`  |
| LEFT      | `Left`              | `btn_left`  | `KeyboardArrowLeft`  |
| RIGHT     | `Right`             | `btn_right` | `KeyboardArrowRight` |

These strings/tags are part of the contract: the instrumented UI test selects buttons by
them, so they must not change without updating the test.
