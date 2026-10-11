# v0.9.5 screenshot and Android verification notes

Captured on 2026-10-11 (Asia/Tokyo) from version 0.9.5, version code 30, on a disposable Android API 36.1 Google APIs Play Store x86_64 emulator. The display was 540 × 1200 pixels at 210 dpi. All quota, reset-credit and activity values are demo fixtures; no real account was connected and no credentials appear in these images.

The debug APK used for capture had SHA-256 `4dfd3909cceda8abf2c0db66cd182817adba000908fca76a4a1d13b6cfe2b92e`. Screenshots are unedited Android `screencap` output. Debug capture activities are excluded from the production APK.

| File | Surface |
| --- | --- |
| `usage-light.png` | Compact four-provider dashboard, English, light appearance |
| `usage-dark.png` | The same dashboard, English, dark appearance |
| `reset-history-ja.png` | Japanese Codex details with available resets and three observed activity fixtures |
| `reset-alert-settings-ja.png` | Japanese settings with reset alerts enabled independently of the disabled persistent notification |
| `gemini-source-ja.png` | Japanese Gemini connection card explaining CLI versus Gemini Apps usage and its manual app link |
| `widget-dual-segments.png` | Real Android widget host, three providers, DUAL_SEGMENTS at 347 × 69 dp |
| `widget-disconnected-edit-ja.png` | Existing widget selections remain editable while Claude, Codex and Antigravity are disconnected |
| `widget-empty-ja.png` | Reopened widget editor preserves an explicitly empty selection and DUAL_SEGMENTS appearance |

## Reproduction

Install the debug APK on a disposable emulator and grant notification permission. Launch `com.codexbar.android/.debug.ScreenshotActivity` with `personal=true`, `antigravity=true` and `reset_history=true`; add `dark_theme=true` for the dark dashboard. Switch the app locale to `ja-JP` for Japanese captures. Navigation and provider-detail sheets use the real Compose UI.

For the widget, grant binding with `adb shell appwidget grantbind --package com.codexbar.android --user 0`, then launch `.debug.WidgetHostActivity` with `seed_demo=true`, `antigravity=true`, `three_services=true`, `width_dp=347`, `height_dp=69` and `template=DUAL_SEGMENTS`. Wait for the real widget publication, rather than capturing the initial fallback. Read the allocated ID from `shared_prefs/debug_widget_host.xml` and launch `.core.widget.WidgetConfigurationActivity` with `appWidgetId` set to that ID. Disable screenshot protection on this demo emulator before capturing the protected editor.

Remove Antigravity and save without adding any credential; the persisted service order becomes `CODEX,CLAUDE`. Reopen, remove both remaining services, save, and reopen again: the stored service order is empty, the template remains DUAL_SEGMENTS and the preview says no services are configured. This verifies the reported disconnect/removal regression through Android UI and persisted widget preferences.

Launch the screenshot activity with `seed_demo=true` and `verify_reset_alerts=true` to run the native notification checks. The v0.9.5 APK logged `CodexBarResetTest: PASS independent alerts, balance content, lock-screen redaction, privacy cancellation and disabled alerts`.

These results cover an Android emulator and real Android widget/notification APIs. They do not replace acceptance on a physical phone, Niagara Launcher, Samsung One UI or Now Bar.
