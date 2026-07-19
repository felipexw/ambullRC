# Phase 0 Research: Home UI & Branding Refresh

## Decision 1: Adaptive icon as raster bitmap layers, not hand-authored vectors

**Decision**: Replace `res/drawable/ic_launcher_background.xml` and
`ic_launcher_foreground.xml` (currently the default Android Studio vector placeholders) with the
supplied `icon-background-432.png` and `icon-foreground-432.png` PNGs, placed in
`res/drawable-nodpi/` under the same resource names so the existing
`mipmap-anydpi/ic_launcher.xml` / `ic_launcher_round.xml` `<adaptive-icon>` definitions keep
resolving without changes.

**Rationale**: The app's `minSdk` is 33 (API 26's adaptive-icon requirement is already always
satisfied), so every install path uses the adaptive icon — there is no legacy pre-adaptive-icon
device to support, and the per-density `mipmap-*/ic_launcher.webp` fallback files become dead
weight the OS never needs on this app's supported devices. `<adaptive-icon>` background/foreground
layers accept any drawable (vector or bitmap) — a bitmap in `drawable-nodpi` is scaled by the
system exactly like the current vectors are, so swapping the file type is a drop-in replacement
with no XML changes. Hand-converting the two PNGs to vector path data would be speculative extra
work with no behavioral benefit (Principle I).

**Alternatives considered**:
- *Regenerate per-density mipmap PNGs (Android Studio Image Asset tool style)*: more files to
  maintain for the same visual result the adaptive-icon XML already delivers automatically from a
  single source layer; rejected as unnecessary duplication.
- *Convert PNGs to vector drawables*: no tooling need justifies it here (Principle I); raster
  layers render identically for this static, non-animated icon.

## Decision 2: Splash screen via native Android 12+ theme attributes, no new dependency

**Decision**: Add `android:windowSplashScreenBackground` and `android:windowSplashScreenAnimatedIcon`
items to the existing `Theme.AmbullRC` style in `res/values/themes.xml` — the same theme already
applied to both `<application>` and `MainActivity` in the manifest. `windowSplashScreenBackground`
points at a new `@color/splash_background` equal to the existing `Background` token's hex value
(`#151210`); `windowSplashScreenAnimatedIcon` points at the same `ic_launcher_foreground` drawable
used for the adaptive icon's foreground layer (Decision 1) — the platform's SplashScreen API draws
this centered over the background color automatically, which is exactly the supplied splash
mock's composition (dark background + centered orange mark).

**Rationale**: The platform SplashScreen API (`android.window.SplashScreen`) has existed since API
31; this app's `minSdk` is 33, so it is unconditionally available with no version-gated resource
directory needed. The `androidx.core:core-splashscreen` compat library exists solely to backport
this behavior to pre-31 devices — since none are supported here, adding it would be a dependency
with no behavior it can add (Constitution Principle I: no dependency without a concrete need). No
custom splash `Activity`, no bespoke Compose splash screen, no new rendering layer — satisfies
FR-007's "no custom splash Activity or bespoke rendering layer."

**Alternatives considered**:
- *`androidx.core:core-splashscreen`*: rejected — see above, zero behavioral gain given `minSdk`
  33, one more dependency to track.
- *A first Compose screen that renders the splash and navigates away*: this is exactly the "bespoke
  rendering layer" the spec rules out (FR-007) and duplicates what the OS already does for free on
  cold start; also reintroduces a visible flash between the OS's automatic default splash and the
  custom one, which the native theme-attribute approach avoids entirely.
- *Rendering the supplied `splash-1080x2400.png` (with "RC CAR" wordmark/subtitle) verbatim as
  `windowSplashScreenBackground`*: the platform splash surface only supports a background color
  (not an arbitrary bitmap) plus a centered icon — recorded as a spec Assumption that the wordmark
  is the mock's own presentation, not a literal requirement.

## Decision 3: Exact color verification — reuse existing tokens, no new hex values

**Decision**: Pixel-sampled all four supplied PNGs directly (`PIL.Image.getcolors()`). Every
non-transparent pixel resolves to one of two colors: background `#151210` and orange
`#FF9A5A`. Both already exist verbatim as `Background` and `Accent` in
`app/src/main/java/com/example/ambullrc/ui/theme/Color.kt` (introduced in feature 005). The new
`@color/splash_background` XML color resource is therefore set to the literal value `#151210` (an
XML color resource cannot reference a Compose `Color` constant, but the hex value is identical —
no drift risk since it's copied from the same source PNGs the Compose token was originally sourced
from).

**Rationale**: Satisfies FR-009 (reuse tokens, don't introduce new hardcoded values) and resolves
the "verify exactly" instruction in the original request — confirmed empirically rather than by
eyeballing.

**Alternatives considered**: None — this was a verification task, not a design choice with
tradeoffs.

## Decision 4: D-pad sizing — `BoxWithConstraints` + weighted rows/cells, not a fixed dp bump

**Decision**: Replace the fixed `CellSize = 76.dp` constant in `ControlScreen.kt` with a
`BoxWithConstraints` wrapping the existing 3-row cross layout. Each of the three `Row`s gets
`Modifier.weight(1f)` inside a `fillMaxSize` `Column`, and each cell (`DirectionButton` /
`CenterHub` / the two blank corner `Box`es) gets `Modifier.weight(1f)` inside its `Row`, with the
existing `GridGap` kept as fixed-dp spacing between them. This makes the cross grid's total
footprint always equal to whatever space `MainActivity` allocates the control region (today:
`Modifier.weight(1f).fillMaxWidth()` between the header and the log panel), and each of the 3×3
cells splits that space evenly — exactly reproducing today's proportions, just scaled up.

**Rationale**: `MainActivity`'s `Column` already gives `ControlScreen` a bounded region via
`Modifier.weight(1f)` between two fixed-height siblings (header, log panel) — so "fill available
space without transpassing the other widgets" is already guaranteed by the parent layout; this
feature only needs the D-pad's *internal* layout to consume 100% of what it's given instead of a
fixed 76dp island centered in extra empty space. Weighted rows/cells are the standard idiomatic
Compose way to do that (Constitution Principle IV: built-in layout primitives only, no custom
measurement logic).

**Alternatives considered**:
- *Bump `CellSize` to a larger fixed dp value*: rejected — still doesn't "fill the whole space" on
  varying screen sizes/densities as the spec asks; would either underfill large screens or overflow
  small ones.
- *Custom `Layout`/`SubcomposeLayout` for pixel-perfect control*: unnecessary complexity — weighted
  `Row`/`Column` already produces the required even 3×3 fill with the existing gap semantics
  (Principle I).

## Decision 5: Header icon — reuse the adaptive icon's foreground drawable directly

**Decision**: Add a Compose `Image(painter = painterResource(R.drawable.ic_launcher_foreground), ...)`
sized ~28dp at the leading edge of `ConnectionStatusBar`'s existing header `Row`, before the device
name `Text`. No new drawable asset is added — the same PNG used for the adaptive icon's foreground
layer (Decision 1) is reused as-is (it's already just the orange car/gamepad line-art on a
transparent background, which reads fine as a small logo on the header's dark `SurfaceAppBar`).

**Rationale**: Avoids adding a fifth image asset for what is visually the same mark at a different
size (Principle I); the existing header `Row`'s `Arrangement.SpaceBetween` /
`verticalAlignment = Alignment.CenterVertically` already accommodates an added leading element
without restructuring.

**Alternatives considered**: *A separate, hand-cropped small-icon asset*: rejected — no visual need
identified; the supplied foreground layer already has the transparent-background + safe-zone
padding a small icon needs.
