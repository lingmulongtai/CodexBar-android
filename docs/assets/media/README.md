# Website media

Optimized copies of the existing native emulator captures. Values and GitHub profiles are synthetic demonstration data; these are not newly captured production screens. The approved app artwork is reused without redrawing.

| Web asset | Source | Size |
| --- | --- | --- |
| `usage-dark.webp` | `docs/images/releases/v0.14.0/usage-dark.png` | 540 x 1200 |
| `usage-light.webp` | `docs/images/releases/v0.14.0/usage-light.png` | 540 x 1200 |
| `history.webp` | `docs/images/releases/v0.14.0/history.png` | 540 x 1200 |
| `spend.webp` | `docs/images/releases/v0.14.0/spend.png` | 540 x 1200 |
| `connections.webp` | `docs/images/releases/v0.16.2/connections-after-drag.png` | 540 x 1200 |
| `github.webp` | `docs/images/releases/v0.15.0/github-overview.png` | 540 x 1200 |
| `github-pinned.webp` | `docs/images/releases/v0.15.0/github-pinned.png` | 540 x 1200 |
| `github-achievements.webp` | `docs/images/releases/v0.15.0/github-achievements.png` | 540 x 1200 |

Provider icons are optimized from `app/src/main/res/drawable-nodpi/provider_*.png`; attribution remains in `docs/provider-icons.md`.

Each screen also has `-270.webp` and `-405.webp` responsive variants (270 × 600 and 405 × 900). They are resized from the original PNG captures above with Pillow LANCZOS and saved as WebP quality 85, method 6. The 540px originals remain the full-size viewer source. `../brand-96.webp` and `../brand-144.webp` are sized from the approved app icon with quality 90, method 6; the PNG is retained for the app manifest.

`../download-qr.png` encodes only `https://lingmulongtai.github.io/CodexBar-android/#download`, never account details or a version-specific APK. It was generated locally using `qrcode` 1.5.4, error correction M, four-module quiet zone, 8px modules, and independently decoded using `jsqr` 1.4.0. No third-party QR service is requested by the site.

`../app-icon.png` (192px) and `../favicon.png` (64px) are size-optimized copies of the approved app artwork. `../og.png` is a 1200 x 630 browser rendering of the landing-page hero, using that artwork and the Usage/GitHub sample screens.
