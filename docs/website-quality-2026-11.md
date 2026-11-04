# Website visitor experience and quality gates — 2026-11-04

Follow-up to [the landing-page refresh](website-refresh-2026-11.md). The approved blue artwork, Liquid Glass direction and “バイブコーディングを、もっと快適に。” headline are retained. The website remains static HTML/CSS/JavaScript on the existing repository's GitHub Pages. There is no new account system, tracking service, external font, QR image API or runtime npm dependency.

## Getting from the website to a working app

- A service picker provides three concrete steps and the relevant owned documentation. Codex and GitHub Copilot can retrieve quotas from a phone; Claude and Gemini CLI use their official local CLI and companion; Gemini Apps uses Windows; the GitHub dashboard is a separate connection from Copilot quotas.
- The Windows guide follows the selected Japanese/English language. Companion sign-in credentials stay on the PC; Android connection settings use Keystore encryption. The page no longer implies every acquisition path calls a provider API directly from Android.
- A native dialog offers a locally generated QR to the canonical download section and a copyable URL. If Clipboard API access fails, the selectable URL and explicit fallback instruction remain usable. The QR was independently decoded; generation details are recorded in [the media inventory](assets/media/README.md).
- Installation help explains the Android install-source permission and updates over an existing app. The 0.16.1/0.16.2 recovery instructions remain available under existing-user help. GitHub support asks for reproducible error information without sign-in codes or tokens.

## Inspecting the product

- Full-size app previews show the currently selected screen, including light/dark and History/Spend, using the retained 540px sources. A failed/loading image cannot be enlarged or mistaken for the newly selected screen; selecting the tab again retries it.
- Native dialogs have visible close controls, contained keyboard focus, Escape dismissal and focus restoration without changing page scroll. Content stays accessible with JavaScript disabled; APK links fall back to the official latest release page.
- Responsive 270/405/540px WebP screens and 96/144px brand images let browsers request the appropriate size. All responsive candidates are checked by the static asset validator. Existing high-resolution originals and the approved PNG manifest icon remain available.
- Visible/accessibility labels match, including the brand and release badge. Navigation/group names and dialog labels follow the selected language. Preferences are optional if browser storage is blocked. CSS and JavaScript cache versions advance together.

## Verification and ongoing gates

- Read-only upstream main checked at `1811d1fe032fd4a80240fd6bc5d04cee649a0afb`; there are no upstream-only commits requiring incorporation or incompatible deferrals. Fork base: `8412c479dc467c28d867f269636aa5de4182417c`.
- Static validator: 78 references, 30 local assets, 29 unique IDs, 17 images; syntax checks passed for all three scripts. No page/text overflow at widths 320, 390, 768, 1440 or 1920 in either language.
- Chromium, Firefox and WebKit each passed eight visitor-journey checks (24 total). axe checks found zero WCAG-tagged violations in the tested Japanese/English layouts and both dialogs. These are automated checks, not a claim that every possible accessibility issue is excluded.
- `ReleaseVersionConsistencyTest` passed against the final HTML. The test checks visible version spans against release metadata rather than requiring a redundant accessibility label. Android application behavior, release number, production signer and update URLs are unchanged.
- One local Lighthouse mobile run: Performance 96, Accessibility 100, Best Practices 100, SEO 100; FCP 1.7s, LCP 2.5s, total blocking time 80ms, CLS 0.014. A local run is environment-dependent and is not directly comparable to the earlier public-site run or a physical phone's performance.
- Pages now requires static validation and all three browser jobs before deployment. Test-only dependencies are isolated in `.github/site-checks`; screenshots are seven-day CI artifacts. See [test commands and coverage](../.github/site-checks/README.md).

The publication report records the actual merged/deployed SHA and public checks. Browser-engine emulation does not establish physical Android-device or Safari-app acceptance.
