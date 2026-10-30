# v0.12.0 release captures

Real Compose UI from the v0.12.0 / versionCode 37 debug APK, captured on 2026-10-31. Quota, cost and token values are synthetic data supplied by ScreenshotActivity; no account credentials or real PC data are present. Daily photography uses the actual cached Unsplash image and visible credit.

- Phone: 1080 × 2400, 420 dpi, font scale 1.0. English and Japanese.
- Tablet: 1920 × 1200, 180 dpi, landscape, two panes.
- Android API 36.1 Google Play x86_64 revision 2, four vCPUs, 4 GiB RAM. Final tooling: isolated stable Android Emulator 37.2.13; software graphics uses GLES SwiftShader / Vulkan Lavapipe. The pre-existing SDK installation was not modified.
- `liquid-glass-phone.png`, `liquid-glass-dark-phone.png`: focused overview and service readings.
- `liquid-glass-spend.png`, `liquid-glass-history.png`: translucent analytics panels and chart controls.
- `liquid-glass-settings.png`, `liquid-glass-connections.png`: real settings/input layouts. Notification permission has not been granted in this fixture; the messages are intentional.
- `liquid-glass-personalization.png`, `liquid-glass-wallpaper.png`, `liquid-glass-language.png`: two theme choices, daily-photo settings, and the language selector.
- `liquid-glass-japanese.png`: Japanese dashboard after choosing Japanese through the glass selector.
- `liquid-glass-tablet.png`: centered floating chrome and two-pane readings.

PNG files are direct emulator screencaps, with fresh UI-tree checks rejecting app/system error dialogs. No screenshots are generated mockups or physical-device evidence. The debug preview uses fixed quota data and omits the live quota refresh action; wallpaper and language controls remain real. Production APKs exclude both debug activities.

Additional input tests ran at 130% font scale in LTR and RTL. Light idle frames over five seconds: dashboard 1 / after scroll 0, Spend 5 / after scroll 0. Dark: dashboard 4 / after scroll 0, Spend 4 / after scroll 0. The verifier also requires a fresh visible app hierarchy, so a stopped/obscured app cannot pass by drawing zero frames.

Earlier testing with Emulator 36.2.11 encountered system-app ANRs and Windows qemu access violations. The newer stable emulator was obtained from the official Google SDK repository and its archive checksum was verified. Those old-tool failures are not counted as passing checks or physical-device results. [Emulator release notes](https://developer.android.com/studio/releases/emulator)

Photo source/license information: [Daily photography](../../../daily-wallpaper.md). API success using a real personal Access Key and physical-device/OEM/battery testing remain unverified.
