# Research: ESP32 Bluetooth Connection on Startup

Resolves the technical decisions the spec deliberately deferred (Bluetooth transport, permissions,
async model, testability seam).

## Decision 1: Transport — Bluetooth Classic (RFCOMM / SPP), not BLE

- **Decision**: Connect using Bluetooth Classic RFCOMM with the Serial Port Profile UUID
  `00001101-0000-1000-8000-00805F9B34FB`, via `BluetoothDevice.createRfcommSocketToServiceRecord`.
- **Rationale**: SPP is a simple bidirectional byte stream — the minimal fit for sending short
  commands, and the natural counterpart to the ESP32 `BluetoothSerial` (SerialBT) API a hobbyist
  uses. It relies on the phone↔ESP32 pairing/bond, which the spec already assumes exists. This is
  markedly simpler than BLE's GATT service/characteristic model (YAGNI, constitution Principle I).
- **Alternatives considered**:
  - **BLE (GATT)**: rejected — more moving parts (services, characteristics, MTU, notifications)
    for no benefit at this scale; power savings are irrelevant for a tethered RC session.
  - **Wi-Fi / TCP to the ESP32**: rejected — the feature explicitly asks for Bluetooth.

## Decision 2: Target device identity — single bonded device by name

- **Decision**: A compile-time constant device name (`Esp32Config.DEVICE_NAME`, e.g.
  `"AmbullRC-ESP32"`) selects the target from `BluetoothAdapter.bondedDevices`. The user sets this
  constant to their ESP32's advertised name (documented in quickstart.md).
- **Rationale**: The spec assumes one known, already-paired ESP32 and rules out discovery/selection
  UI. Looking up a bonded device by name needs no scanning (and therefore no location/scan
  permission), keeping both the code and the permission surface minimal.
- **Alternatives considered**: device picker over discovered devices (rejected: out of scope, adds
  scanning + `BLUETOOTH_SCAN` permission + UI); hardcoded MAC address (rejected: less legible than a
  name and still device-specific — a name constant is equally simple and clearer to change).

## Decision 3: Permissions — `BLUETOOTH_CONNECT` only, requested at startup

- **Decision**: Declare `android.permission.BLUETOOTH_CONNECT` in the manifest and request it at
  runtime from `MainActivity` (via `ActivityResultContracts.RequestPermission`). No `BLUETOOTH_SCAN`
  and no location permission.
- **Rationale**: On Android 13 (minSdk 33), connecting to an already-bonded device needs only
  `BLUETOOTH_CONNECT`. We never scan/discover, so `BLUETOOTH_SCAN`/location are unnecessary. Fewer
  permissions = simpler and less intrusive. If the operator denies it, the app surfaces
  `Failed(PERMISSION_DENIED)` and offers retry (FR-003).
- **Alternatives considered**: legacy `BLUETOOTH`/`BLUETOOTH_ADMIN` with `maxSdkVersion` (rejected:
  irrelevant at minSdk 33); requesting scan/location up front (rejected: not needed, more friction).

## Decision 4: Async model — coroutines + AndroidX ViewModel

- **Decision**: `ConnectionViewModel` extends `androidx.lifecycle.ViewModel` and runs connect /
  monitor work in `viewModelScope` on `Dispatchers.IO`. Add `kotlinx-coroutines-android` and
  `androidx.lifecycle:lifecycle-viewmodel-ktx`. The connect attempt is wrapped in `withTimeout` so a
  stalled attempt fails within the SC-003 bound.
- **Rationale**: Opening an RFCOMM socket is a blocking call that must not run on the main thread and
  must be cancellable when the ViewModel clears. `viewModelScope` gives correct lifecycle-scoped
  cancellation; coroutines give a clean timeout and structured connect→monitor sequencing. These
  dependencies are the minimal idiomatic tools for async Android I/O — the constitution permits new
  dependencies when the task genuinely needs them, and this one does. (Feature 001's synchronous
  `ControlViewModel` stays a plain class; it is not refactored — YAGNI.)
- **Alternatives considered**: raw `Thread` + `Handler` (rejected: more error-prone lifecycle and
  cancellation handling than coroutines); plain-class ViewModel with a hand-managed `CoroutineScope`
  (rejected: reimplements what `viewModelScope` already provides correctly).

## Decision 5: Testability seam — `Esp32Connection` interface with typed exceptions

- **Decision**: Define `interface Esp32Connection { suspend fun connect(); suspend fun
  awaitDisconnect(); fun disconnect() }`. `connect()` throws typed exceptions
  (`BluetoothDisabledException`, `DeviceUnavailableException`, `LinkException`) on failure;
  `awaitDisconnect()` suspends until an established link drops. `BluetoothEsp32Connection` is the
  real implementation; `FakeEsp32Connection` (test source) simulates success, each failure type, and
  a controllable drop. The `ConnectionViewModel` maps exception types → `FailureReason`.
- **Rationale**: The Android Bluetooth classes are `final`/framework-bound and unusable in JVM unit
  tests. A single small seam lets every branch of the state machine (success, each failure reason,
  retry, mid-session drop) run deterministically against a fake — satisfying constitution Principle
  V's "test double for the Bluetooth API" requirement with zero hardware. It is also exactly the
  attach point the future command-sending feature extends.
- **Alternatives considered**: Robolectric to fake Bluetooth (rejected: new heavy test dependency,
  and the platform Bluetooth stack is not meaningfully emulated); testing only on-device (rejected:
  non-deterministic, needs hardware, violates the "runs without ESP32" requirement).

## Decision 6: Detecting a dropped link (FR-008) without reading telemetry

- **Decision**: After `connect()` succeeds, `BluetoothEsp32Connection.awaitDisconnect()` blocks on a
  single `inputStream.read()`; when it returns `-1` or throws `IOException`, the link is considered
  dropped. Any byte read is discarded. The `ConnectionViewModel` observes this and transitions to
  `Failed(CONNECTION_LOST)`.
- **Rationale**: RFCOMM exposes link loss through stream EOF/IOException; a blocking read is the
  simplest liveness signal. Discarding bytes keeps this within the one-way-command scope (Principle
  III) — we are not consuming telemetry, only detecting closure. The fake models this with a
  `CompletableDeferred` the test completes to simulate a drop, so the behavior is fully unit-tested.
- **Alternatives considered**: periodic keep-alive writes (rejected: introduces outbound traffic and
  a protocol this feature doesn't need); ignoring drops (rejected: violates FR-008).

## Decision 7: Status UI — stateless `ConnectionStatusBar(state, onRetry)`

- **Decision**: A stateless composable renders the current `ConnectionState` as text and shows a
  Retry button only when the state is `Failed`. `MainActivity` collects the ViewModel's
  `StateFlow` (`collectAsState`) and places the bar above the existing `ControlScreen`.
- **Rationale**: A stateless composable is trivially and robustly UI-testable by passing each state
  directly, independent of the ViewModel/coroutines. Matches function-over-form.
- **Alternatives considered**: a dedicated connection screen with navigation (rejected: over-built);
  embedding status logic inside the composable (rejected: breaks MVVM and testability).
