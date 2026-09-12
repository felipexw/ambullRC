<!--
Sync Impact Report
==================
Version change: 2.0.0 → 2.1.0
Modified principles: none (no principle redefined in a backward-incompatible
  way)
Added sections/content:
  - Hardware & Communication Scope: actuator list expanded from "exactly two
    actuators" (servo + DC motor) to include a lights on/off accessory as a
    third supported actuator, plus a documented "simple discrete accessory"
    pattern so a future single accessory (e.g. a speaker, not built yet) can
    be added later via a small amendment instead of re-litigating scope.
  - Hardware & Communication Scope: narrow exception added to the one-way-
    remote rule, permitting the app to read back an accessory's own on/off
    state (e.g. lights) while steering/throttle commands remain strictly
    one-way (no telemetry read-back for drive actuators).
Removed sections: none
Templates requiring updates:
  - .specify/templates/plan-template.md ✅ no changes required (Constitution
    Check gate is generic/data-driven; will pull updated scope from this
    file at plan time)
  - .specify/templates/spec-template.md ✅ no changes required (project-agnostic)
  - .specify/templates/tasks-template.md ✅ no changes required (no
    actuator/one-way references)
  - .claude/skills/speckit-*/SKILL.md ✅ reviewed, no stale references found
  - CLAUDE.md ✅ updated project-summary line to mention the lights accessory
  - AGENTS.md ✅ updated project-summary line to mention the lights accessory
Follow-up TODOs: none. The speaker accessory itself is explicitly NOT being
  added now — only the documentation pattern that will let it be added later
  without a scope debate, per Principle I (YAGNI): no code or abstraction for
  it exists yet.
-->

# AmbullRC Constitution

## Core Principles

### I. Simplicity & YAGNI (NON-NEGOTIABLE)

Build only what the current feature requires. Do not add abstractions,
configuration options, design patterns, or extensibility hooks for
hypothetical future needs (multiple vehicles, multiple protocols, plugin
architectures, etc.) unless a concrete requirement exists today. If a simpler
implementation satisfies the requirement, it MUST be preferred over a more
"correct" or "extensible" one. This is a learning side project, not a
commercial product — complexity has no payoff here and directly works
against the goal of understanding the system end-to-end.

### II. MVVM Architecture

The app MUST follow Model-View-ViewModel: Views (Activities/Composables)
contain no business logic and only render state and forward user actions;
ViewModels hold UI state and expose it via observable holders
(StateFlow/LiveData) and contain the logic that decides which command to
send; Models represent the Bluetooth connection, the ESP32 command protocol,
and any persisted settings. Views MUST NOT talk to the Bluetooth layer
directly — all communication is mediated through a ViewModel. This keeps the
Bluetooth/hardware logic testable in isolation from Android UI framework
classes.

### III. Single Purpose: Command Transmission Only

The application's entire job is to translate user input (e.g., a steering
slider/buttons, a throttle control) into discrete commands and transmit them
to the ESP32 over Bluetooth. The app MUST NOT grow scope into things like
telemetry dashboards, data logging/analytics, multi-device management, user
accounts, or cloud sync unless the user explicitly requests such a feature.
When in doubt about whether something is in scope, the default answer is no.

### IV. Delightful UX, Simple Implementation

The app's UX and look-and-feel MUST be delightful, not merely functional:
clear visual feedback for every action (button press/release states,
connection status, command activity), smooth motion, and a coherent, polished
theme are expected outcomes, not nice-to-haves. This principle does NOT
relax Principle I — delight MUST be achieved through simple, idiomatic means:
prefer Compose/Material 3's built-in theming and animation primitives
(`MaterialTheme`, `animateXAsState`, `AnimatedVisibility`, standard
`Modifier` effects) over custom rendering, bespoke animation frameworks,
third-party design libraries, or new architectural layers built solely to
support visual effects. If a delightful result requires real architectural
complexity (new abstractions, extra layers, non-standard dependencies), scale
back the visual ambition rather than the simplicity of the implementation.

### V. Mandatory Test Coverage

Every feature MUST work correctly and MUST ship with automated tests before
it is considered done:

- Unit tests are REQUIRED for all ViewModel logic and any command-encoding /
  protocol logic (pure logic, no Android framework or real Bluetooth
  hardware involved).
- Integration tests are REQUIRED for the Bluetooth communication layer,
  exercising connection setup, command send/receive paths, and
  disconnect/error handling against a fake or test double for the
  Bluetooth API (no real ESP32 hardware required to run the suite).
- A feature without passing unit and integration tests MUST NOT be merged
  to `main`, regardless of how small it is.

Tests are not optional scaffolding in this project — they are the primary
replacement for manual QA. Polishing UX per Principle IV does not create an
exemption: interaction/animation logic that can be expressed as ViewModel
state still needs unit coverage.

## Hardware & Communication Scope

- The ESP32 peer controls two drive actuators — one servomotor (rear-wheel
  steering) and one DC motor (drive/engine) — plus a small, explicitly
  enumerated set of simple discrete accessories that are switched, not
  driven: currently just the lights (on/off). The app's command surface MUST
  map directly to this enumerated list — steering position/angle,
  throttle/speed (plus stop/neutral), and each accessory's on/off state —
  and MUST NOT be generalized into an arbitrary multi-channel or
  plugin-style command system that accepts channels/accessories not
  explicitly listed here.
- Additional simple discrete accessories (each a single on/off switch, no
  variable range) MAY be added later by amending this list — e.g. a speaker
  is anticipated but intentionally NOT implemented yet, per Principle I
  (YAGNI): no code, abstraction, or protocol support for it exists until a
  feature concretely requires it and this section is amended to name it.
  This bullet documents the pattern so adding one more such accessory is a
  small, expected amendment rather than a re-litigation of scope; it is not
  a blanket allowance for arbitrary or unlisted accessories.
- Communication is over Bluetooth. The command protocol (message format,
  framing, whether Classic SPP or BLE is used) is a technical decision made
  in the implementation plan for the relevant feature, not fixed here — but
  whatever is chosen MUST stay minimal (e.g., a small fixed-size message or
  simple delimited text), consistent with Principle I.
- The app is primarily a one-way remote control: sending commands to the
  ESP32 is the primary path, and the two drive actuators (steering,
  throttle) remain strictly one-way — the app MUST NOT read their state back.
  The one narrow exception is that the app MAY read back a listed discrete
  accessory's own on/off state (e.g. lights) over Bluetooth, so the UI can
  display what the ESP32 actually confirms rather than the app's own guess.
  Reading back anything else (drive-actuator telemetry, sensor data,
  diagnostics) remains out of scope unless a future feature explicitly
  requires it and amends this section accordingly.

## Development Workflow & Quality Gates

- This is a solo learning project: there is no external PR review
  requirement, but every change MUST still satisfy Principle V (unit +
  integration tests passing) before being considered complete.
- Favor Android's standard tooling defaults (Gradle, JUnit, standard
  Android instrumentation/test libraries) over introducing new frameworks,
  DI containers, or build tooling — adopt a new dependency only when the
  task genuinely cannot be done reasonably without it.
- Keep the module/package structure flat and aligned with MVVM (e.g.,
  `ui`/`view`, `viewmodel`, `model` or `data`) rather than layering in
  additional architectural boundaries (use cases/interactors, repository
  abstractions over a single data source, etc.) unless a real need for that
  indirection appears.

## Governance

This constitution supersedes ad-hoc preferences for this project. Any
practice that conflicts with a principle here (e.g., adding UX polish via a
new architectural layer instead of idiomatic Compose primitives, adding
speculative abstractions, skipping tests) MUST be corrected or the
constitution MUST be amended first — it should not be silently overridden.

**Amendment procedure**: Since this is a solo project, the project owner may
amend this constitution directly by editing this file. Every amendment MUST
update the version number below per semantic versioning:

- MAJOR: a principle is removed or redefined in a backward-incompatible way
  (e.g., dropping mandatory tests, abandoning MVVM).
- MINOR: a new principle or section is added, or existing guidance is
  materially expanded.
- PATCH: wording clarifications, typo fixes, or non-semantic edits.

**Compliance review**: Before starting implementation of any feature (at
`/speckit-plan` time), the Constitution Check gate MUST confirm the planned
approach honors Principles I–V above. Any deviation MUST be recorded and
justified in that feature's plan.md Complexity Tracking table.

**Version**: 2.1.0 | **Ratified**: 2026-07-17 | **Last Amended**: 2026-09-11
