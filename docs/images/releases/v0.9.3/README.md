# v0.9.3 release captures

Captured on 2026-09-25 14:48 +0900 from the v0.9.3 debug APK (Android version code 28), on a disposable Android API 36.1 emulator at 540 × 1200 pixels / 210 dpi, font scale 1.0. These are newly captured Android views using synthetic quotas, not screenshots from a previous release.

| Capture | Verification |
| --- | --- |
| `widget-dual.png`, `widget-segments.png` | Existing templates 9 and 6 at exactly 347 × 69dp |
| `widget-dual_segments.png`, `widget-segments_dual.png` | New templates 11 and 12; three providers, independent quota bars, visible percentages and reset labels |
| `usage-light.png`, `usage-dark.png` | Official provider artwork and compact Japanese Usage screen in both themes |
| `usage-order.png`, `usage-reordered.png` | Up/down controls move Claude above Codex and immediately update the cards |
| `widget-order.png` | Separate numbered order controls; moving Codex below Copilot updates the live preview |

The unchanged implementation was checked with 329 Android unit tests, Android lint (zero errors, 119 warnings), and all 28 native widget-host cases before version preparation. The v0.9.3 version-consistency test and debug build then passed. All four layouts listed here were rechecked for fully visible provider names, primary/secondary quota percentages and resets on the v0.9.3 APK. Usage and widget reorder interactions were also repeated.

Claude companion verification: 18 tests, syntax checks, Windows native PTY smoke and production dependency audit passed (zero vulnerabilities). PR CI additionally exercises Claude on Windows, macOS and Linux, all Android tests/lint, and the Gemini/Codex companions. The tag workflow builds signed artifacts, verifies the pinned signer, smoke-tests the signed APK and packaged Claude companion, and publishes checksums/SBOMs/provenance.

Debug APK SHA-256: `0bd5a3381ee9f8c74f93076c8fe7249cec6ff25a4b1ae7e0bd4646cbd6653c4a`. This checksum identifies the capture input, not the signed release APK.

## Reproduction and boundaries

Use a disposable emulator only: the fixture activities replace widget cache and privacy settings. Start `WidgetHostActivity` with `seed_demo=true`, `three_services=true`, width 347, height 69 and the selected template ID; use `ScreenshotActivity` with `personal=true` for the compact Usage fixture. `WidgetStudioActivity` uses `seed_demo=true`. All fixture activities are excluded from release builds.

The full native regression script is `scripts/verify-widget-host.py`. Release captures used the GPU host renderer after the earlier software-rendered emulator showed delays under concurrent build load. Production timeouts and fallbacks were not changed.

Niagara Launcher on the user's physical phone remains an independent acceptance target. These captures do not claim physical-phone validation or authenticated-account verification. Existing local Claude/Tailscale pairing was confirmed separately by the user, not by these demo screenshots.
