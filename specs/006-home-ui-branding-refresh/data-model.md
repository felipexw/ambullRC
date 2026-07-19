# Phase 1 Data Model: Home UI & Branding Refresh

This feature introduces no new Kotlin data types, no ViewModel state, and no persistence — it is a
layout/resource/theme change to an existing stateless UI. The only "entities" involved are Android
resource assets and derived layout dimensions, documented below for completeness.

## Entity: App Icon (Android resource, not Kotlin)

| Layer | Resource | Source | Notes |
|---|---|---|---|
| Background | `res/drawable-nodpi/ic_launcher_background.png` | `assets/icon-background-432.png` | Solid `#151210` fill. |
| Foreground | `res/drawable-nodpi/ic_launcher_foreground.png` | `assets/icon-foreground-432.png` | Orange (`#FF9A5A`) car/gamepad line-art, transparent background, standard adaptive-icon safe-zone padding already baked in. |
| Monochrome (Android 13+ themed icon) | same as foreground | — | Unchanged reference in `mipmap-anydpi/ic_launcher.xml` — the system uses only the foreground's alpha channel for themed tinting, so pointing it at the new foreground PNG is correct without further edits. |

`mipmap-anydpi/ic_launcher.xml` / `ic_launcher_round.xml` are unchanged — both already reference
`@drawable/ic_launcher_background` / `@drawable/ic_launcher_foreground` by name, which now resolve
to the new PNGs instead of the old vectors.

## Entity: Splash Screen (Android theme configuration, not Kotlin)

| Attribute | Value | Notes |
|---|---|---|
| `android:windowSplashScreenBackground` | `@color/splash_background` = `#151210` | Full-screen background color shown on cold start, matching `Background`/`#151210`. |
| `android:windowSplashScreenAnimatedIcon` | `@drawable/ic_launcher_foreground` | Same PNG as the adaptive icon's foreground layer — centered by the platform automatically. |

Both are added as `<item>`s inside the existing `Theme.AmbullRC` style (`res/values/themes.xml`),
applied via the manifest's existing `android:theme="@style/Theme.AmbullRC"` on both `<application>`
and `MainActivity` — no new theme, no new style hierarchy.

## Entity: D-pad Cell Dimension (derived, not stored)

Replaces the fixed `CellSize = 76.dp` constant in `ControlScreen.kt`.

| Attribute | Type | Notes |
|---|---|---|
| Row weight | `Modifier.weight(1f)` × 3 | Each of the three grid rows claims an equal third of the control region's height. |
| Cell weight | `Modifier.weight(1f)` × 3 (per row) | Each of the three cells in a row (blank/button/blank, or button/hub/button) claims an equal third of the row's width. |
| Gap | `GridGap` (unchanged, 10.dp) | Fixed-dp spacing between cells/rows — does not scale with the region, matching today's visual rhythm at a larger size. |

Not a new state — resolved once per composition/recomposition by Compose's existing layout pass
via the parent `MainActivity` Column's `Modifier.weight(1f)` region for `ControlScreen`. No
ViewModel involvement; `connected`/press-state handling in `DirectionButton` is unchanged (see
contracts/ui-contract.md).

### State transitions

None — this feature adds no new state machine. `ConnectionState`, press-interaction state, and the
log panel are all untouched.
