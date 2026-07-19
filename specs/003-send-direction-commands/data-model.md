# Data Model: Send Direction Commands to ESP32

No persistence. The "data" here is the outbound command message and the seam operation that
transmits it. See [contracts/command-contract.md](./contracts/command-contract.md) for exact
signatures.

## Entity: Direction Command (transient, not a stored type)

The outbound message representing exactly one button tap. Not a new class — it is the string
produced from an existing `Direction` value at the moment `send` is called.

| Direction (existing enum) | Wire message |
|----------------------------|--------------|
| `Direction.UP`              | `"UP\n"`     |
| `Direction.DOWN`             | `"DOWN\n"`   |
| `Direction.LEFT`             | `"LEFT\n"`   |
| `Direction.RIGHT`            | `"RIGHT\n"`  |

- **Validation / invariants**:
  - Exactly one message is produced per tap (FR-003).
  - The message identifies one, and only one, of the four directions and carries no other data
    (FR-004).
  - Message content is fixed by `Direction.name` — no new encoding table to keep in sync.

## Operation: `Esp32Connection.send`

Extends the existing seam interface (feature 002) with one new operation:

- `suspend fun send(message: String): Boolean` — writes `message` to the live link and returns
  `true` on success. Returns `false` (never throws) when there is no live connection or the write
  fails for any reason (FR-005, FR-006).

### State dependency

`send` behaves differently depending on the connection lifecycle already modeled by
`ConnectionState` (feature 002), even though `ControlViewModel` never reads that state directly —
the seam's internal socket presence is the single source of truth:

| Underlying link state | `send` result |
|------------------------|----------------|
| No `connect()` has succeeded yet, or `disconnect()`/a drop has occurred since | `false`, no bytes written |
| A live socket exists and the write succeeds | `true` |
| A live socket exists but the write throws `IOException` | `false` |

No new state machine is introduced; this table is a projection of `ConnectionState` (`Connected`
vs. everything else) onto `send`'s return value, enforced inside the seam implementation.
