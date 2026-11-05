# Adaptive tablet layout

The Liquid Glass visual design is shared across phone, cover-screen, unfolded,
tablet, landscape and resizable desktop windows. Layout decisions use the app's
available window space rather than the device model. All dimensions below are dp.

## Page geometry

- The pinned header glass spans the usable window on every page, including
  Connections and Settings. Its content aligns to a centered 1440 dp region on
  very wide windows; the glass itself continues to the edges.
- Scrolling content still passes under the header. The continuous horizontal
  pager retains one fixed body viewport; per-page header heights do not resize it.
- An available height below 480 dp uses a compact header with unchanged control
  targets and text sizes. Horizontal safe-drawing insets protect side cutouts and
  landscape navigation areas.
- Main overviews are centered within 1440 dp, forms and GitHub widgets within
  1120 dp, and repository reading lists within 1040 dp. These are content limits,
  not limits on the header, wallpaper or bottom glass navigation.

## Usage and Trends

Usage starts as a full-width quota overview. It adds a persistent service-detail
pane only when the usable window is at least 1120 dp wide and 600 dp tall. Text
scaling raises those thresholds. The detail pane is 384 dp wide before font
scaling; the rest of the window remains available to the overview. Short windows
and typical foldable inner-screen widths keep the readable overview and existing
detail sheet instead of squeezing both panes together.

Usage and Trends choose one, two or three card columns from actual available
width, including side padding and gaps. Cards have a minimum nominal width of
340 dp multiplied by font scale (never reduced below 1). Trends keeps its pinned
History/Spend selector aligned with the overview.

## Connections and Settings

Connections uses one or two provider columns, with a minimum nominal provider
width of 420 dp multiplied by font scale. Expanded connection forms, connection
summary, desktop-bridge configuration and search span the whole content area.
Reordering retains the existing saved order and accessible actions.

Settings uses one or two columns with a minimum nominal section width of 460 dp
multiplied by font scale. Its small, finite set of keyed sections stays composed
inside one vertical scroll container: scrolling or changing column count within
the current activity must not discard unsaved form text or cancel a
section-owned save operation. API keys
are not copied into saved-instance-state bundles. Attribution remains after the
scrollable content.

The bottom navigation is hidden while the IME occupies the window so it does not
cover form fields. Explicit navigation clears field focus and dismisses the IME.

## Repeatable checks

Focused unit tests cover width/height thresholds, font scaling, padding/gaps and
existing navigation/header source contracts. A debug APK exposes synthetic
ScreenshotActivity data for captures without connecting real accounts.

The emulator driver refuses physical devices and requires a dedicated CodexBar
AVD. It records the installed APK's identity, waits for stable UI hierarchies,
rejects ANR overlays and new ANR events, records every tab, opens Settings,
checks the header settings action is near the correct trailing edge, then
verifies the GitHub header after returning. A capture without these checks is
not accepted. Results, hierarchy XML and failure diagnostics go into the
requested output folder. Configuration is applied once before initial app
launch; tab changes and subsequent resizes exercise the retained app.

```powershell
python scripts/verify-adaptive-layout.py --serial emulator-5582 --output output/tablet/en
python scripts/verify-adaptive-layout.py --serial emulator-5582 --output output/tablet/ja --locale ja --case 800,1280 --case 1280,800
python scripts/verify-adaptive-layout.py --serial emulator-5582 --output output/tablet/large-text --font-scale 1.3 --case 840,720 --case 1280,800
```

The default matrix is 360×800, 600×900, 840×720, 800×1280, 1280×800,
1600×1000 and 900×400. It tests window-size adaptation, not a physical foldable
hinge. Occluding hinges, tabletop posture, actual device graphics performance and
the owner's final visual acceptance remain separate hardware checks. A failed or
incomplete capture run must not be reported as a passed matrix.

### Initial local verification, 2026-11-06

- Strict dependency verification, `:app:assembleDebug`, `:app:testDebugUnitTest`
  and `:app:lintDebug`: successful from a fresh local build directory.
- 164 unit-test suites, 524 tests: 522 passed, 2 skipped, 0 failures, 0 errors.
- Lint: 0 errors/fatal issues, 191 warnings. This task does not claim to resolve
  all pre-existing lint concerns.
- Emulator captures are **not accepted visual evidence**. API 35 and API 36.1
  fixtures with host/software rendering exhibited System UI or app ANR dialogs.
  The pre-change main APK also exhibited the problem. Direct captures without UI
  Automation were attempted and remained blocked by System UI ANR. The cause is
  not established, so no complete size/language/theme/interaction matrix is
  reported as passing. Actual-device fold posture and visual acceptance remain
  unverified.

Read-only upstream main checked at
`1811d1fe032fd4a80240fd6bc5d04cee649a0afb`; no upstream-only changes were
available to incorporate or defer for this task.
