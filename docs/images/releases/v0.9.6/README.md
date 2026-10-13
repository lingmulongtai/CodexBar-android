# v0.9.6 captures and verification

Captured on 2026-10-14 (Asia/Tokyo) from Android 0.9.6 / version code 31 on the disposable CodexBar_Screenshot_API_36_1 emulator, Android API 36.1, 540 × 1200 pixels at 210 dpi and font scale 1.0. Images are unedited Android screencap output. All usage values are synthetic; no real account was connected and no pairing key appears in the images.

The final capture/debug APK SHA-256 was `4ed46fc88594c4dc6416dc93f51422f2ccb0627df81a9b40a5ab7114d2e95535`.

| File | Surface |
| --- | --- |
| usage-light.png | Existing debug-only four-provider dashboard fixture, light appearance |
| usage-dark.png | The same fixture in dark appearance |
| windows-usage.png | Normal Android dashboard receiving the actual optimized Windows app's synthetic 25% used / 75% left observation |
| windows-connections.png | Actual saved Windows pairing and Codex source selection, English |
| windows-connections-ja.png | The same connection controls in Japanese |
| settings-ja.png | Japanese notifications and reset-alert settings |
| widget-dual-segments.png | Real Android widget host with three synthetic providers at 347 × 69 dp |

Windows verification used the optimized native Win-CodexBar Mobile executable, an isolated profile, the real TCP/HMAC/AES-GCM implementation, and the Android Connections UI. The pairing code used the emulator's 10.0.2.2 host route. A temporary ADB reverse mapping was removed before normal dashboard verification. Android Keystore persistence was verified by updating the debug APK without uninstalling; Windows DPAPI persistence was verified by restarting the optimized executable.

Windows key rotation rejected the old key; the new key worked. Disabling sharing from the real UI with an invalid address draft closed the listener and preserved the valid saved endpoint. Unknown observation/account values are kept unknown; the synthetic Windows seed has no real account subject, so it does not fabricate account history.

The existing ScreenshotActivity fixture uses `personal=true`, `antigravity=true`, `reset_history=true`; add `dark_theme=true` for dark captures. The widget uses `.debug.WidgetHostActivity` with `seed_demo=true`, `antigravity=true`, `three_services=true`, `width_dp=347`, `height_dp=69`, `template=DUAL_SEGMENTS`, after granting appwidget binding. Capture activities are excluded from release APKs. Screenshot protection was disabled only on this disposable demo emulator for the normal application captures.

These checks cover native Windows and an Android emulator. Physical phones, live accounts, Niagara Launcher and Samsung Now Bar are separate acceptance targets. Gemini means Gemini CLI; Google Gemini Apps usage collection remains unsupported.