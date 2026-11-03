# GitHub profile widgets

## Owner's design direction

- Preserve the approved Liquid Glass header, dimensions, scrolling under it, wallpaper and bottom navigation. The settings action remains visible on every main tab; it does not fade when entering a tab.
- Body transitions move only on X. Measure the natural header and any Trends selector before placing content, keeping its destination Y fixed even while the shared header height animates.
- Add profile information as optional, reorderable widgets. Keep the existing header Edit / add / Done controls, long-press reorder and continuous calendar resizing.
- Pinned repositories belong to one container, in selection order, with at most two tiles in a row. Show the repository name without the owner prefix, visibility, description, language and stars. Keep search/selection on a separate screen.

## Widgets and sources

| Widget | Source and behavior |
| --- | --- |
| Achievements | Public GitHub profile HTML: a badge link and matching trusted asset image are both required. Shows confirmed tier and public PR progress where known. A count below a confirmed badge's tier floor uses that known lower bound, marked `+`. Missing badges say not shown; failure to recognize any badge says unavailable. |
| Local achievements | Derived from the rolling calendar: maximum daily activity, longest streak, annual total and active days. Explicitly separate from GitHub-awarded badges. |
| Year in review | Last 365 days: contributions, best day, current/longest streak, active days, busiest weekday by average (including zero days), monthly totals. A zero so far today does not break yesterday's streak. |
| Contribution calendar | Last 365 days, native Canvas, blue intensity steps, month labels, legend, selected-day outline. One staggered entrance animation per data identity, using the app-owned Dev animation speed. Resizing does not restart it. |
| Tech stack | First public profile README `skillicons.dev` list; second list is learning. No guesses from repository languages. Bundled native vector icons with a text fallback for unknown technologies. |
| Portrait | Avatar converted to a native ASCII-style rendering, without transmitting it to another service. |
| Pinned repositories | Repository metadata already shared with this GitHub App. Existing individual pin widgets migrate into one ordered, deduplicated group; account ID isolation and unavailable saved IDs are preserved. |

The existing Today / This week / This month / This year commit counts retain their calendar-period meaning. A contribution calendar includes issues, PRs and reviews as well as commits.

Reference: [profile README](https://github.com/lingmulongtai/lingmulongtai), especially `scripts/fetch_achievements.py`, `render_achievements.py`, `fetch_contributions.py`, `render_stats.py` and `render_heatmap.py`. The app adapts the data rules and composition to native glass widgets; it does not embed the generated profile SVGs or fix a user's account in production source.

## Networking and saved data

Public badge HTML, public profile README and public merged-PR search use fixed GitHub HTTPS endpoints without credentials. Badge PNGs use only the matching GitHub assets path; avatars use only the GitHub avatar host. Redirects remain disabled; response, image-size and timeout limits remain in effect. No additional login is required for these public additions. The existing private contribution authorization and account checks remain in place.

Badges rely on GitHub's public page markup because the current integration has no achievement API. The parser supports both the legacy `/images/modules/profile/achievements/` and current `/assets/` forms; it degrades to unavailable if GitHub changes the markup. Accounts without an authored stack list show an explanatory empty state.

## Verification and demo

Read-only upstream main checked at `1811d1fe032fd4a80240fd6bc5d04cee649a0afb`; no upstream-only changes to incorporate or defer. Fork acquisition modes, stored connections, signer, update URLs and release workflow are preserved.

`ScreenshotActivity --ez github_demo true --es destination github` uses deterministic synthetic statistics and repositories from `src/debug` only. Optional `--es github_section stats|achievements|stack|pins` focuses each widget. No demo credentials or layout are written to preferences; the release build ignores the debug state override. Public badge artwork and the requested profile's public avatar may be downloaded to illustrate the native widgets.

Focused tests cover first-frame header geometry, legacy pin migration/account boundaries, public badge/stack parsing, URL restrictions, tokenless requests, and rolling statistics/streaks. Full debug tests, lint and APK assembly are also run. Demo screenshot evidence is emulator-only; the owner's physical device was not connected for this iteration.

Final debug verification: 489 tests, 487 passed, 2 skipped (the explicitly opt-in native Windows interoperability proof profile was not configured); lint and APK assembly succeeded. Captures used a disposable read-only API 36.1 emulator. The initial SwiftShader emulator process crashed in native host code; switching the capture emulator to the host GPU completed the screens without an Android runtime crash. No physical-device result is claimed.
