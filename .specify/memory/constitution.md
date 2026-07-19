<!--
Sync Impact Report
==================
Version change: 1.0.0 → 2.0.0
Modified principles:
  - IV. Function Over Form (UX Is Not a Priority) → IV. Delightful UX, Simple
    Implementation (backward-incompatible redefinition: the app now MUST
    invest in polished, delightful UX/interactions, reversing the prior
    "don't spend effort on styling" stance; architectural simplicity remains
    non-negotiable and now explicitly bounds how that polish is achieved)
Added sections: none
Removed sections: none
Also updated: Principle V rationale and Governance example clause, which
  cross-referenced the old "UX doesn't matter" stance of Principle IV and
  would otherwise now read as self-contradictory.
Templates requiring updates:
  - .specify/templates/plan-template.md ✅ no changes required (Constitution
    Check gate is generic/data-driven; will pull updated Principle IV from
    this file at plan time)
  - .specify/templates/spec-template.md ✅ no changes required (project-agnostic)
  - .specify/templates/tasks-template.md ✅ no changes required (Polish phase
    wording already generic; now also covers UX-delight tasks under Principle IV)
  - .claude/skills/speckit-*/SKILL.md ✅ reviewed, no stale references found
  - CLAUDE.md ✅ updated Principle IV bullet to match new wording
  - AGENTS.md ✅ updated Principle IV bullet to match new wording
Follow-up TODOs: none.
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

- The ESP32 peer controls exactly two actuators: one servomotor (rear-wheel
  steering) and one DC motor (drive/engine). The app's command surface MUST
  map directly to these two actuators — steering position/angle and
  throttle/speed (plus stop/neutral) — and MUST NOT be generalized into an
  arbitrary multi-channel or plugin-style command system.
- Communication is over Bluetooth. The command protocol (message format,
  framing, whether Classic SPP or BLE is used) is a technical decision made
  in the implementation plan for the relevant feature, not fixed here — but
  whatever is chosen MUST stay minimal (e.g., a small fixed-size message or
  simple delimited text), consistent with Principle I.
- The app is a one-way remote control: sending commands to the ESP32 is the
  primary path. Reading telemetry/state back from the ESP32 is out of scope
  unless a future feature explicitly requires it.

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

**Version**: 2.0.0 | **Ratified**: 2026-07-17 | **Last Amended**: 2026-07-19
