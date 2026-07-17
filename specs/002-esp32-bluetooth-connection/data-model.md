# Data Model: ESP32 Bluetooth Connection on Startup

No persistence. The "data" here is the in-memory connection state and the seam's operations. See
[contracts/connection-contract.md](./contracts/connection-contract.md) for exact signatures.

## Entity: `ConnectionState` (sealed)

The single source of truth the ViewModel exposes and the UI/tests observe.

| State        | Meaning | Carries |
|--------------|---------|---------|
| `Idle`       | No connection attempt in progress or started yet | — |
| `Connecting` | An attempt is underway | — |
| `Connected`  | A live RFCOMM link to the ESP32 exists | — |
| `Failed`     | The last attempt failed, or an established link dropped | `reason: FailureReason` |

- **Validation / invariants**:
  - `Connected` is entered **only** after `Esp32Connection.connect()` returns successfully (FR-005,
    SC-005). No code path sets `Connected` optimistically.
  - Exactly one state is current at any time (a `StateFlow` value).
- **State transitions**:
  - `Idle → Connecting` — startup auto-connect (FR-001) or `retry()` from `Failed`.
  - `Connecting → Connected` — `connect()` succeeded.
  - `Connecting → Failed(reason)` — `connect()` threw, or the attempt timed out.
  - `Connected → Failed(CONNECTION_LOST)` — `awaitDisconnect()` completed (link dropped, FR-008).
  - `Failed → Connecting` — `retry()` (FR-007).

```text
        startup / retry
Idle ───────────────► Connecting ──success──► Connected
 ▲                        │                        │
 │                        │ fail / timeout         │ link dropped
 │                        ▼                        ▼
 └──────────────────── Failed(reason) ◄────────────┘
             retry()
```

## Entity: `FailureReason` (enum)

Closed set describing why the connection is not established. Drives the status text.

| Value               | Cause | Maps from |
|---------------------|-------|-----------|
| `PERMISSION_DENIED` | Operator denied `BLUETOOTH_CONNECT` | `ConnectionViewModel.onPermissionDenied()` |
| `BLUETOOTH_DISABLED`| Phone Bluetooth is off | `BluetoothDisabledException` |
| `DEVICE_UNAVAILABLE`| Target ESP32 not bonded / not found / not reachable / connect timed out | `DeviceUnavailableException`, `TimeoutCancellationException` |
| `CONNECTION_LOST`   | An established link dropped | `awaitDisconnect()` completion |
| `ERROR`             | Any other link/IO failure | `LinkException` / unexpected `IOException` |

## Entity: `Target Device` (config, not runtime data)

- `Esp32Config.DEVICE_NAME: String` — the bonded ESP32's advertised name to match against
  `BluetoothAdapter.bondedDevices`.
- `Esp32Config.SPP_UUID: UUID` — the Serial Port Profile UUID `00001101-0000-1000-8000-00805F9B34FB`.

These are constants, not persisted or user-entered in this feature.

## Operations seam: `Esp32Connection`

Behavioral contract (full signatures in the contract file):

- `suspend fun connect()` — suspends until a live link is established; throws a typed
  `Esp32ConnectionException` subtype on failure. Must not return normally unless the link is up.
- `suspend fun awaitDisconnect()` — suspends while the link is up; resumes when it drops.
- `fun disconnect()` — closes the link/resources; idempotent.
