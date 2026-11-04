# Website refresh — 2026-11-04

The existing GitHub Pages landing page now follows the approved blue CodexBar icon and the Android app's Liquid Glass direction. The static HTML/CSS/JavaScript architecture and the existing Pages workflow are retained.

## Visitor experience

- Product-first introduction using the existing Usage and GitHub emulator screens. No fabricated customer counts, testimonials or account data.
- An accessible four-tab app tour with keyboard navigation; Usage offers light/dark and Trends offers History/Spend. Only the latest requested image may update the preview; a failed image has a visible error and can be retried.
- Japanese and English, using the browser language initially and retaining an explicit language choice when local storage is available. Blocked storage does not break switching.
- GitHub contribution, achievement and pinned-repository widgets; Windows pairing and Tailscale; privacy and installation guidance.
- APK and release-note links derive from the existing SoftwareApplication version metadata. Without JavaScript they lead to the owned latest Release page.
- The recovery notice for 0.16.1/0.16.2 is placed beside the download, rather than in the tablet feature description. Costs remain estimates and provider/acquisition differences remain explicit.
- Optimized WebP screenshots, approved PNG app/favicons and a 1200 × 630 sharing image rendered from the landing page. Original source mapping is in `assets/media/README.md`.

## Local checks

- `.github/scripts/validate-pages.py`: passed (47 references, 16 local assets, 20 unique IDs, 16 images).
- `node --check docs/assets/site.js`: passed. The current Android release version assertions still match the landing page.
- Chromium at 320×800, 390×844, 768×1024, 1440×1000 and 1920×1080, in both Japanese and English: no horizontal page/text overflow, empty translation, or broken image.
- Tour selection, light/dark, History/Spend, rapid selection, arrow-key selection, language persistence and localized metadata: passed.
- Mobile menu, Escape, dismissal after navigation, image-failure/retry, reduced-motion preference, JavaScript-disabled content/download fallback and storage-blocked language switching: passed.
- Existing native screenshots use synthetic data. Browser QA does not claim new Android-device, Safari or physical-device validation.

## Upstream and publication

Read-only upstream main checked at `1811d1fe032fd4a80240fd6bc5d04cee649a0afb`; no upstream-only commits required incorporation or deferral. Work is based on released fork main `9e57ebbe2beca72c46dc2de11d4d77e30897a37e`.

Publication uses the existing pull-request checks and main-branch GitHub Pages deployment. The deployment SHA and public response are verified at completion; this local record does not substitute for successful deployment.
