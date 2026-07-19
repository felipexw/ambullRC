# UI / Resource Contract: Home UI & Branding Refresh

This is an Android app with no external/network API. The contract it exposes is the UI surface
(Composables) and Android resources user actions and tests bind to. This contract only covers what
**changes** in this feature — `ConnectionState`, `Esp32Connection`, `ConnectionViewModel`,
`ControlViewModel`, and `LogEntry`/`DebugLog` are all unchanged (see features 002/003/005's
contracts).

## `ControlScreen` (Composable, modified)

```kotlin
@Composable
fun ControlScreen(
    viewModel: ControlViewModel,
    connected: Boolean,
    modifier: Modifier = Modifier,
)
```

- **Signature unchanged** — same params as feature 005.
- **Renders**: the same cross layout (four `DirectionButton`s + decorative `CenterHub`), now sized
  via `BoxWithConstraints` + weighted rows/cells (research.md Decision 4) instead of a fixed 76dp
  `CellSize`, so the grid expands to fill whatever region `modifier` is given.
- **Removed**: the hint `Text` ("Hold a direction to drive" / "Waiting for connection to enable
  controls") is removed **only for the connected case** — when `connected == true`, no hint text
  renders at all. When `connected == false`, the existing "Waiting for connection to enable
  controls" text is unchanged.
- **Behavior unchanged**: press/release still call `viewModel.onDirectionPressed` /
  `onDirectionReleased`; disabled/dimmed appearance when `connected == false` is unchanged aside
  from operating at the new larger size.
- **Guarantees** (asserted by instrumented tests):
  - Each direction button's measured size is larger than the old fixed 76dp cell on a standard test
    device size (FR-001, SC-001).
  - No button/hub bounds fall outside `ControlScreen`'s own bounds (FR-002).
  - When `connected == true`, no node with the old "Hold a direction to drive" text exists
    (FR-004).
  - When `connected == false`, the "Waiting for connection to enable controls" text still exists,
    unchanged (FR-004 edge case).

## `ConnectionStatusBar` (Composable, modified)

```kotlin
@Composable
fun ConnectionStatusBar(
    state: ConnectionState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
)
```

- **Signature unchanged** — pure visual addition to an existing contract.
- **Renders**: a new leading brand icon (`painterResource(R.drawable.ic_launcher_foreground)`,
  ~28dp) before the device name `Text`, in every connection state; everything else (device name,
  status pill, Retry button) is unchanged.
- **Guarantees** (asserted by instrumented tests):
  - The brand icon is present in the header regardless of `state` (FR-005).
  - The status pill and Retry button (when shown) remain visible and unclipped alongside the new
    icon on a standard test device width (FR-005).

## App Icon & Splash Screen (Android resources, no Kotlin API)

- **App icon**: `res/drawable-nodpi/ic_launcher_background.png` /
  `ic_launcher_foreground.png` replace the prior vector placeholders; referenced unchanged by
  `mipmap-anydpi/ic_launcher.xml` / `ic_launcher_round.xml` (FR-006).
- **Splash screen**: `Theme.AmbullRC` (`res/values/themes.xml`) gains
  `android:windowSplashScreenBackground` (`@color/splash_background`, `#151210`) and
  `android:windowSplashScreenAnimatedIcon` (`@drawable/ic_launcher_foreground`) — shown
  automatically by the platform on cold start, dismissed automatically once `MainActivity` reports
  ready, no app code required (FR-007, FR-008).
- **Guarantees** (verified manually per quickstart.md — no automated test surface exists for
  launcher icons or the platform splash screen):
  - Installed app shows the new icon in the launcher/app drawer/recents (FR-006).
  - Cold launch shows the dark background + orange mark before the home screen appears; warm
    resume does not replay it (FR-007, FR-008).
