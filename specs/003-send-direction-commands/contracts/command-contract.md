# Command Contract: Send Direction Commands to ESP32

Android app, no network API. The contract is the extended Bluetooth seam + `ControlViewModel` that
the app and the tests bind to. Builds directly on
[002's connection contract](../../002-esp32-bluetooth-connection/contracts/connection-contract.md)
— `Esp32Connection` gains one method, nothing else in that contract changes.

## `Esp32Connection` (seam) — new operation

```kotlin
interface Esp32Connection {
    suspend fun connect()          // unchanged, from feature 002
    suspend fun awaitDisconnect()  // unchanged, from feature 002
    fun disconnect()               // unchanged, from feature 002

    /** Writes [message] to the live link. Returns true if handed off successfully; false if there
     *  is no live connection or the write failed for any reason. Never throws. */
    suspend fun send(message: String): Boolean
}
```

- **Real implementation** `BluetoothEsp32Connection` (in `data/`): captures the socket's
  `outputStream` alongside `inputStream` in `connect()`. `send` writes `message` UTF-8-encoded and
  flushes; returns `false` if `outputStream` is `null` (never connected / already disconnected) or
  an `IOException` is thrown; clears `outputStream` in `disconnect()` alongside the existing
  `inputStream` cleanup.
- **Test implementation** `FakeEsp32Connection` (in `test`): tracks `isConnected` (`true` after a
  successful `connect()`, `false` after `disconnect()`/`simulateDrop()`), records every accepted
  message in an ordered `sentCommands: List<String>`, and exposes a `sendShouldSucceed: Boolean`
  knob to simulate a write failure while still connected.

## `ControlViewModel` (AndroidX `ViewModel`)

```kotlin
class ControlViewModel(
    private val connection: Esp32Connection,
    private val logger: DirectionLogger = AndroidDirectionLogger(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    /** Logs [direction] (existing behavior, unchanged) and sends its command over [connection] on
     *  [ioDispatcher]. Never throws back to the caller regardless of send outcome. */
    fun onDirectionTapped(direction: Direction)
}
```

- **Guarantees** (asserted by unit tests, using `kotlinx-coroutines-test` virtual time and
  `FakeEsp32Connection`):
  - Each of the four directions sends its own matching message (`"UP\n"`, `"DOWN\n"`, `"LEFT\n"`,
    `"RIGHT\n"`) — FR-001, FR-002.
  - One tap produces exactly one `send` call / one recorded message — FR-003.
  - `N` taps (any mix of directions) produce `N` recorded messages, in tap order — FR-003.
  - Tapping while `FakeEsp32Connection.isConnected == false` calls `send` (so the seam can reject
    it) but records no message and does not throw — FR-005, FR-006.
  - Existing logging behavior (one `DirectionLogger.log` call per tap, matching direction) is
    unchanged — regression coverage for feature 001.

## `MainActivity` wiring (extends feature 002's wiring)

- Constructs exactly one `BluetoothEsp32Connection` instance and provides it to **both**
  `ConnectionViewModel` and `ControlViewModel` via their `viewModelFactory` initializers, so sent
  commands and the connection lifecycle share the same socket.
- Passes the `ControlViewModel` instance into `ControlScreen(viewModel = controlViewModel)`
  (`ControlScreen`'s default no-arg `ControlViewModel()` is removed since the ViewModel now
  requires a connection).

## Out of scope (per spec Assumptions)

- No acknowledgment/response is read back from the ESP32 for a sent command.
- No queueing or replay of a tap made while disconnected.
- No new commands beyond the four existing directions.
