# Data Model: Direction Buttons Screen

This feature has a single, trivial domain type and one UI-facing behavior contract. No
persistence, no relationships, no external schema.

## Entity: `Direction`

The set of four discrete directions the control screen exposes. Modeled as a Kotlin enum.

| Value   | Meaning (this feature) | Content description | Test tag    |
|---------|------------------------|---------------------|-------------|
| `UP`    | Up button tapped       | `"Up"`              | `btn_up`    |
| `DOWN`  | Down button tapped     | `"Down"`            | `btn_down`  |
| `LEFT`  | Left button tapped     | `"Left"`            | `btn_left`  |
| `RIGHT` | Right button tapped    | `"Right"`           | `btn_right` |

- **Fields**: none beyond the enum identity. (Intentionally no icon/label fields baked
  into the enum — the Composable maps each value to its Material icon and description at
  the UI layer, keeping the Model framework-free per Principle II.)
- **Validation rules**: the type is closed — exactly these four values exist. Any tap
  maps to exactly one value; there is no "none"/"invalid" case to handle at runtime.
- **Relationships**: 1:1 between each on-screen button and one `Direction`; 1:1 between
  each logged record and one `Direction`.
- **State transitions**: none. `Direction` is a stateless value; tapping does not change
  any persisted or in-memory state in this feature (it produces a log record only).

### Future note (not built now)

`Direction` is the seam the future command-transmission feature will map onto the ESP32
actuators (throttle forward/reverse, steering left/right). That mapping is **out of
scope** here and MUST NOT be added preemptively (Principle I / III).

## Behavioral contract: tap → log

Not an entity, but the core rule the tests assert (see [contracts/ui-contract.md](./contracts/ui-contract.md)):

- Each tap on a button invokes `ControlViewModel.onDirectionTapped(direction)` with that
  button's `Direction`.
- `onDirectionTapped` produces exactly one log record identifying that `Direction`.
- Repeated taps each produce their own record; a tap on one button never produces a
  record for another direction.
