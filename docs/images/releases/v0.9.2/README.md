# v0.9.2 verification captures

Captured on 2026-09-25 from the v0.9.2 debug APK (version code 27), Android
16.1 / API 36.1, 1080 × 2400 pixels at 420 dpi, font scale 1.0. These are
fresh captures of Android views using demo quotas, without connected accounts.
ScreenshotActivity, WidgetHostActivity, WidgetStudioActivity, and the pin receiver
are debug-only components excluded from the signed release.

| Capture | What was exercised |
| --- | --- |
| `widget-ledger.png`, `widget-meters.png`, `widget-columns.png` | Three providers at exactly 347 × 69dp in a real AppWidgetHost |
| `widget-tiles.png`, `widget-rings.png`, `widget-segments.png` | Tile, ring, and segmented quota treatments at the same size |
| `widget-vertical.png`, `widget-focus.png`, `widget-dual.png`, `widget-reset.png` | Vertical meters, focused provider, dual windows, and reset-first layouts |
| `niagara-columns.png` | Niagara Launcher v1.16.29, production widget resized using Niagara controls; reported content size 315 × 68dp |
| `usage-light.png`, `usage-dark.png` | Compact three-provider Usage screen and fixed small header |
| `usage-detail.png` | Tapping a compact card retains the detailed quota screen |
| `widget-studio.png` | Native widget preview, ten layout choices, and appearance controls |

## Reproduction

Install the debug APK on a disposable emulator. The demo harness overwrites
widget cache and privacy settings, so do not run it on a phone with real data.

```sh
python scripts/verify-widget-host.py --adb /path/to/adb --serial emulator-5554 \
  --screenshots docs/images/releases/v0.9.2
```

The script checks ten size/privacy cases across process restarts, including
legacy hosts without `OPTION_APPWIDGET_SIZES`, plus all ten templates at
347 × 69dp. It waits for stable visible text after the cached fallback and full
render have had time to settle. A previous host frame or basic fallback does
not count as a successful full render. Names, percentages, and reset labels
are checked, and every captured template is visually inspected.

Local validation also includes 325 Android unit tests, the debug build, and
Android Lint (zero errors; 114 warnings). Companion checks cover 11 Claude,
7 Gemini, and 5 Codex tests, the Windows native PTY smoke test, and npm production
dependency audits (zero reported vulnerabilities in all three companions).

```sh
adb shell am start -W -S -n com.codexbar.android/.debug.ScreenshotActivity \
  --ez personal true --es theme MATERIAL_3 --ez dark_theme false
adb shell am start -W -S -n com.codexbar.android/.debug.WidgetStudioActivity \
  --ez seed_demo true
```

For Niagara, retain the pinned production widget, then seed demo values and
appearance for it before returning Home:

```sh
adb shell am start -W -S -n com.codexbar.android/.debug.WidgetHostActivity \
  --ez seed_demo true --ei width_dp 347 --ei height_dp 69 \
  --ez three_services true --es template COLUMNS --ez update_all_demo true
```

The Niagara screenshot demonstrates its own 315 × 68dp allocation. The exact
347 × 69dp requirement is separately exercised by AppWidgetHost. These checks
do not establish physical-phone acceptance, every launcher/font configuration,
or authenticated Claude/Devin account behavior. Devin response/error handling
is tested with MockWebServer. Claude requires the user's CLI sign-in and
Android pairing; no credentials are embedded in the captures.
