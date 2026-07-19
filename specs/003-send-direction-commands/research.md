# Research: Send Direction Commands to ESP32

No open technical unknowns require external investigation — this feature extends an existing,
already-decided architecture (feature 002's `Esp32Connection` seam) with one new operation. The
decisions below are the planning-time technical choices the spec deliberately deferred (see
spec.md Assumptions).

## Decision: Extend `Esp32Connection` with a non-throwing `send`

**Decision**: Add `suspend fun send(message: String): Boolean` to the existing `Esp32Connection`
interface. Returns `true` if the message was handed to the socket successfully, `false` if there
is no live connection or the write failed — it never throws.

**Rationale**: FR-005/FR-006 require that a tap while disconnected, or any transport failure, is
silently absorbed with no crash and no visible feedback beyond the existing status bar. A boolean
result lets `ControlViewModel` treat "not connected" and "write failed" identically with a plain
`if`, matching Principle I (simplest thing that works) — no new exception hierarchy, no
`try`/`catch` at the call site. `connect()` keeps using exceptions (feature 002) because callers
there need to distinguish *why* a connect failed for the status UI; `send()` has no such
requirement (FR-004: the message carries direction identity only).

**Alternatives considered**:
- *Reuse `Esp32ConnectionException` and throw from `send()`*: rejected — forces `ControlViewModel`
  to add exception handling for a case the spec explicitly wants to be a silent no-op.
- *Expose a separate `isConnected: Boolean` on the seam and gate the call in `ControlViewModel`*:
  rejected — duplicates connection-state tracking that already lives inside the seam/socket, and
  couples `ControlViewModel` to connection internals it doesn't otherwise need.

## Decision: Message format is the direction name plus a newline delimiter

**Decision**: Each command is `Direction.name` (`"UP"`, `"DOWN"`, `"LEFT"`, `"RIGHT"`) followed by
a trailing `\n`, UTF-8 encoded — e.g. `"UP\n"`.

**Rationale**: FR-002 requires four distinct, unambiguous messages; the four enum names already
satisfy that with zero extra encoding logic (Principle I). A trailing newline is the simplest
framing that lets a typical Arduino/ESP32 sketch use `Serial.readStringUntil('\n')` to read one
command per line — the constitution requires *some* minimal framing decision at this layer, and a
newline-delimited plain-text token is the smallest one that works. No length prefix, checksum, or
binary encoding is introduced (would violate Principle I with no current requirement driving it).

**Alternatives considered**:
- *Raw enum ordinal as a single byte*: more compact, but unreadable/undebuggable over a serial
  monitor and not "just a string" as the spec explicitly asked for.
- *JSON payload (`{"dir":"UP"}`)*: unnecessary structure for a single scalar value; rejected under
  Principle I and Constitution's "small fixed-size message or simple delimited text" guidance.

## Decision: `ControlViewModel` becomes an AndroidX `ViewModel` with an injected dispatcher

**Decision**: `ControlViewModel` changes from a plain class to `androidx.lifecycle.ViewModel`,
gains a `viewModelScope.launch { withContext(ioDispatcher) { connection.send(...) } }` call, and
takes an injected `CoroutineDispatcher` (default `Dispatchers.IO`), mirroring `ConnectionViewModel`
from feature 002.

**Rationale**: Writing to a `BluetoothSocket.outputStream` is blocking I/O; per the project's own
architecture conventions (CLAUDE.md: "ViewModels use `viewModelScope`, `StateFlow`, and an injected
`CoroutineDispatcher`") this must not run on the main thread, and must be swappable for a test
dispatcher so unit tests are deterministic and don't touch real I/O. This exactly matches the
pattern already established and tested in `ConnectionViewModel`, so no new pattern is introduced.

**Alternatives considered**:
- *Keep `ControlViewModel` a plain class and launch on `GlobalScope`*: rejected — leaks coroutines
  past the Activity/ViewModel lifecycle and contradicts the project's stated ViewModel convention.
- *Make `send` a blocking (non-suspend) call on the caller's thread*: rejected — would block the UI
  thread on every tap.

## Decision: One shared `Esp32Connection` instance wires both ViewModels

**Decision**: `MainActivity` constructs a single `BluetoothEsp32Connection` instance and passes it
to both `ConnectionViewModel` (connection lifecycle) and `ControlViewModel` (command sending) via
their `viewModelFactory` initializers.

**Rationale**: A command can only be sent on the same live socket that `ConnectionViewModel`
established. Since `Esp32Connection` is already the single seam covering both concerns, sharing one
instance is the minimal way to make sent commands reach a real, connected socket — no new
coordination layer, no observing `ConnectionState` from `ControlViewModel` (see previous decision).

**Alternatives considered**:
- *Merge `ControlViewModel` into `ConnectionViewModel`*: rejected — mixes two different
  responsibilities (connection lifecycle vs. per-tap command decisions) into one class, and breaks
  the existing tested `ControlViewModel`/`ConnectionViewModel` separation for no benefit.
