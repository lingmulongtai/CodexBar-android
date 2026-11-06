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

### Completed local verification, 2026-11-06

- Strict dependency verification, `:app:assembleDebug`, `:app:testDebugUnitTest`
  and `:app:lintDebug`: successful from a fresh local build directory.
- 164 unit-test suites, 524 tests: 522 passed, 2 skipped, 0 failures, 0 errors.
- Lint: 0 errors/fatal issues, 191 warnings. This task does not claim to resolve
  all pre-existing lint concerns.
- Debug v0.16.4/version code 49 passed **12 window/language/theme cases and 60
  native screen captures**. Each case covers Usage, Trends, Connections, GitHub
  and Settings, including Settings return. English dark appearance covers all
  seven sizes above; Japanese covers 800×1280 and 1280×800; Japanese at 1.3× font
  scale covers 840×720 and 1280×800; light appearance covers 1280×800. All use
  synthetic data. Accepted runs contain no ANR overlay or new ANR event.
- Focused interaction checks cover the visible pinned header while the keyboard
  is open, keyboard dismissal on navigation, dock restoration, retained provider
  search, two-column provider cards, full-width form expansion, cross-column
  drag ordering and restoring automatic order. The span cache is invalidated
  together with form expansion so the expanded card does not disappear.
- Navigation checks pass all nine Settings return paths (selected tab, Back and
  immediate Back from Trends, Connections and GitHub), plus holding adjacent
  pages during a horizontal drag, reversing the drag and cancelling back to
  Usage. The held-frame capture retains the same vertical page origin.
- Website browser checks: all eight tests pass in each of Chromium, Firefox and
  WebKit. The Firefox screenshot-tour check waits for the requested image to
  finish decoding, in addition to waiting for the busy indicator to clear.
  A prior CI run spent about 48 minutes fetching Ubuntu packages from the Azure
  archive mirror before starting browser tests. Browser jobs now use the official
  Playwright 1.64.1 Noble image, pinned by registry digest and matching the locked
  npm dependency, with browsers and system dependencies already installed. The
  same three browser suites remain required; each job has a 15-minute bound.

### Emulator failure diagnosis and working configuration

The initial captures were rejected because they contained System UI/app ANR
dialogs. The pre-change main APK also exhibited the failure. Windows Application
Error/Windows Error Reporting records subsequently established native QEMU
access violations (`0xc0000005`) in emulator 36.2.11 and an isolated official
37.2.13 on the software OpenGL ES path. Logs show that `-gpu swangle` selected
`swiftshader_indirect`/SwiftShader on this Windows host. Updating the emulator
alone did not resolve that path. There is no symbolized native stack, so the
faulting instruction and a specific upstream defect are not established.

The completed matrix uses the isolated official **37.2.13** binary, API 35,
`-gpu host -feature -Vulkan`, NVIDIA RTX 4050/OpenGL ES 3.0, four virtual CPUs,
4096 MB RAM and no snapshots. Its dedicated AVD has a 1280×800 physical buffer,
160 dpi and 32-bit color; the driver changes the logical window size for each
case. The installed SDK and unrelated AVDs are unchanged. This is a verified
working configuration on this host, not a claim that all emulator graphics
backends are fixed.

A separate early Pixel Launcher ANR reported no focused window after input sent
immediately following `sys.boot_completed`. The bootstrap now waits for System
UI to draw before sending input and does not send the early unlock key event.
The accepted matrix and interaction checks then completed without new ANRs or
native emulator termination. The driver also rejects ANR-covered screenshots
and stores failure diagnostics instead of accepting a fixed-delay capture.

Representative captures are in [`images/releases/v0.16.4`](images/releases/v0.16.4).
Physical fold/hinge posture, OEM cutouts, hardware graphics performance and
tactile feedback remain unverified on real devices.

Read-only upstream main checked at
`1811d1fe032fd4a80240fd6bc5d04cee649a0afb`; no upstream-only changes were
available to incorporate or defer for this task.
