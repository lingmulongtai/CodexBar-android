# Provider customization verification

Captured on 2026-09-25 from the real Android UI and Glance RemoteViews, using synthetic quota fixtures on a disposable Android API 36.1 emulator. No real credentials or account data are included.

Open [the visual comparison](index.html) to compare existing templates 6 and 9 with additions 11 and 12, the official provider artwork, and the Usage/widget order controls. Original screenshots are retained; the HTML only clips the surrounding emulator space for the widget comparison.

## Behavior

- Usage: the labeled Reorder / 並び替え action opens numbered providers with accessible up/down controls. The dashboard stores the chosen order separately from widget settings and notification priority; automatic ordering can be restored.
- A refresh preserves a custom order. Unknown names and duplicates are discarded; newly connected providers follow saved providers, and disconnected providers retain their saved slots for reconnection.
- Widget: Display order / 表示順 is now a separate card above the live preview. Both directions are available and changing order updates the preview.
- Template 11 (`DUAL_SEGMENTS`) keeps template 9's rows and side-by-side windows, with segmented bars.
- Template 12 (`SEGMENTS_DUAL`) keeps template 6's provider columns and gives both quota windows their own segmented bar. A provider with only one window still has one bar.
- Existing template IDs and positions are unchanged. Both additions respect hidden secondary windows, redaction, and the narrow-width fallback.
- All 19 provider icons use bundled first-party artwork with original colors. See [sources and notices](../../provider-icons.md).

## Checks

- `:app:testDebugUnitTest`: 329 tests passed, zero failures/errors/skips.
- `:app:lintDebug`: zero errors, 119 warnings (including existing warnings and non-blocking resource/style warnings).
- `:app:assembleDebug`: passed. Debug APK SHA-256: `e6ed3360a40a9349a0165b25c3ac0899ef1ab408bdc9805effdaf96299a05c4c`.
- `scripts/verify-widget-host.py`: 28 cases passed: ten size/legacy/privacy cases, all twelve templates at 347 × 69 dp, and six new-template cases for secondary visibility, redaction and 240 dp width.
- Actual UI interaction moved Claude above Codex from the Usage sheet, then verified the card coordinates changed accordingly. See [reordered cards](usage-reordered.png).
- Actual widget editor interaction moved Codex below Copilot; both numbered rows and the live preview reflect this order.
- Japanese labels and official icons were visually checked in the light/dark Usage screens and order editors.

The first software-rendered emulator run exceeded the host check deadline under concurrent build/emulator load and showed the existing basic fallback. The complete 28-case rerun passed after switching to host GPU rendering and separating the build from UI checks. No production rendering timeout or safety fallback was loosened.

## Limits

These screenshots are emulator evidence, not a physical Niagara Launcher acceptance test. No release tag, signed release or publication was created for this change. The APK is a debug build; it is not an in-place signed update for the installed release app.
