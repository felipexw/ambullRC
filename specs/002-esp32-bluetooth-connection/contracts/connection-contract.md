# Connection Contract: ESP32 Bluetooth Connection

Android app, no network API. The contract is the Bluetooth seam + ViewModel + status UI that the
app, the tests, and the future command-sending feature bind to.

## `ConnectionState` + `FailureReason` (model)

```kotlin
sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data class Failed(val reason: FailureReason) : ConnectionState
}

enum class FailureReason {
    PERMISSION_DENIED, BLUETOOTH_DISABLED, DEVICE_UNAVAILABLE, CONNECTION_LOST, ERROR
}
```

See [data-model.md](../data-model.md) for the state machine and invariants.

## `Esp32Connection` (seam) + exceptions

```kotlin
interface Esp32Connection {
    /** Suspends until a live RFCOMM link to the ESP32 exists. Throws an Esp32ConnectionException
     *  subtype on failure. Returns normally ONLY when the link is up. */
    suspend fun connect()

    /** Suspends while the link is up; resumes when it drops (EOF / IO error). Undefined if called
     *  before a successful connect(). */
    suspend fun awaitDisconnect()

    /** Closes the link and releases resources. Idempotent; safe to call in any state. */
    fun disconnect()
}

sealed class Esp32ConnectionException(message: String) : Exception(message)
class BluetoothDisabledException : Esp32ConnectionException("Bluetooth is disabled")
class DeviceUnavailableException : Esp32ConnectionException("ESP32 not available")
class LinkException(cause: String) : Esp32ConnectionException(cause)
```

- **Real implementation** `BluetoothEsp32Connection` (in `data/`): uses
  `BluetoothManager`/`BluetoothAdapter`; if the adapter is off → `BluetoothDisabledException`; finds
  the bonded device named `Esp32Config.DEVICE_NAME`, else `DeviceUnavailableException`; opens an
  RFCOMM socket to `Esp32Config.SPP_UUID`, mapping `IOException` → `DeviceUnavailableException` (open
  failed) or `LinkException`. `awaitDisconnect()` blocks on `inputStream.read()` and returns on
  `-1`/`IOException`, discarding bytes.
- **Test implementation** `FakeEsp32Connection` (in `androidTest`/`test`): configurable to (a)
  connect successfully, (b) throw a chosen exception, and (c) complete `awaitDisconnect()` on demand
  to simulate a drop. Records call counts for assertions.

## `ConnectionViewModel` (AndroidX `ViewModel`)

```kotlin
class ConnectionViewModel(
    private val connection: Esp32Connection,
    private val connectTimeoutMillis: Long = 12_000L
) : ViewModel() {

    val state: StateFlow<ConnectionState>   // starts Idle

    /** Begin an attempt: Idle/Failed → Connecting → Connected | Failed(reason). Then monitor for
     *  drop → Failed(CONNECTION_LOST). Wrapped in withTimeout(connectTimeoutMillis). */
    fun connect()

    /** Retry after a failure. Equivalent to connect() from a Failed state. */
    fun retry()

    /** Called by the Activity when BLUETOOTH_CONNECT was denied → Failed(PERMISSION_DENIED). */
    fun onPermissionDenied()
}
```

- **Guarantees** (asserted by unit tests, using `kotlinx-coroutines-test` virtual time):
  - `connect()` emits `Connecting` then `Connected` when the seam connects (FR-001, FR-005, SC-001).
  - Each seam exception maps to the matching `Failed(reason)` per the data-model table (FR-006, SC-003).
  - A connect that never completes fails as `Failed(DEVICE_UNAVAILABLE)` at the timeout (SC-003).
  - `onPermissionDenied()` → `Failed(PERMISSION_DENIED)` and never calls `connect()` on the seam (FR-003).
  - `retry()` from `Failed` re-attempts and can reach `Connected` (FR-007, SC-004).
  - After `Connected`, when `awaitDisconnect()` completes → `Failed(CONNECTION_LOST)` (FR-008).
  - `state` never equals `Connected` unless the seam's `connect()` returned successfully (SC-005).
  - `onCleared()` calls `connection.disconnect()`.

## `ConnectionStatusBar` (Composable, stateless)

```kotlin
@Composable
fun ConnectionStatusBar(
    state: ConnectionState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
)
```

- Renders status text keyed by state, each carrying a stable test tag:

| State | Text (indicative) | Test tag |
|-------|-------------------|----------|
| `Idle` | "Not connected" | `status_text` |
| `Connecting` | "Connecting…" | `status_text` |
| `Connected` | "Connected" | `status_text` |
| `Failed(reason)` | "Not connected — <reason>" | `status_text` |

- Shows a **Retry** button (`testTag = "btn_retry"`, content description "Retry") **only** when
  `state is Failed`; tapping it calls `onRetry`. No Retry button in any other state.
- Contains no logic beyond mapping state → text; never touches Bluetooth or the ViewModel directly.

## `MainActivity` wiring

- On create: request `BLUETOOTH_CONNECT`. Granted → `connectionViewModel.connect()`. Denied →
  `connectionViewModel.onPermissionDenied()`.
- Provides the real `BluetoothEsp32Connection` to the ViewModel via a `ViewModelProvider.Factory`
  (`by viewModels { … }`), collects `state` with `collectAsState`, and composes
  `ConnectionStatusBar(state, onRetry = viewModel::retry)` above the existing `ControlScreen`.

## Manifest

```xml
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```
