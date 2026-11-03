# Continuous paging, provider reordering and optional haptics — 2026-11-03

Base: `5fee369299dc3b1aeab0b6089395cb9ac5e1f44f` (released v0.16.1). Branch: `codex/continuous-pager-navigation`.

## Upstream

Fetched the read-only upstream remote at the beginning of this task. Checked upstream main `1811d1fe032fd4a80240fd6bc5d04cee649a0afb`; it has no upstream-only commits relative to this fork. No compatible updates were missing and no incompatible updates were deferred. Acquisition modes, credentials, connection settings, existing widget settings, production signing, update URLs and release workflow were preserved.

## Result

- Usage, Trends, Connections and GitHub share a real horizontal pager. Held swipes move adjacent pages together, reverse without changing the destination, and retain scroll state. Tab taps scroll the same complete track without pre-jumping distant pages.
- The glass header remains pinned and records the moving bodies beneath it. Each body keeps its own fixed vertical origin, independent of the visible header height. The persistent settings action has no entry fade.
- Settings and Dev settings occupy a separate pager above the retained tabs. Back and tapping the originating tab return to that tab. An in-progress pane entry also has a Back target. Only the active page handles modal UI, Back and Dev previews.
- Usage, Trends and Connections save separate provider orders. History and Spend share the Trends order. Filtering and temporarily disconnected providers do not erase saved positions. Reorder mode uses the accepted Usage controls and blocks body tab swipes.
- Horizontal chart/filter edges do not hand their gesture to the page pager. The programmatic-only pane and History/Spend tracks do not accept descendant nested-scroll input. Dragged provider cards use the existing pointer-preserving translation without a second placement animation.
- Settings → Haptic feedback defaults on, persists in the existing preferences store and applies immediately. Tab/segmented presses and switch/slider lifts request one light SegmentTick; holding, dragging, release and programmatic changes do not repeat it. A shared 80 ms gate coalesces overlapping requests. Turning it off silences existing reorder feedback too. Preferences must load before feedback is enabled.

## Verification

- Final `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` with strict dependency verification: **PASS**.
- Unit tests: **518 total, 516 passed, 0 failures/errors, 2 skipped**. The skips are the opt-in Windows native bridge snapshot and concurrent-revision interoperability cases.
- Lint: **0 errors**, 191 warnings and 4 hints remain in the generated report; this task did not expand into unrelated warning cleanup.
- Python navigation verification script syntax: **PASS**.
- Disposable Android API 36.1 emulator, 540 × 1200, dark Liquid Glass, synthetic Usage/GitHub data: **PASS** for each of Trends, Connections and GitHub → Settings → originating tab, including selected-tab tap, Back and Back during entry (9 return paths).
- Held adjacent-page swipe, reverse/cancellation, header staying fixed, and fractional bar-lens movement: **PASS**, with native screenshots/video.
- Trends provider long-press drag: **PASS**; the demo Codex card moved below Cline/Claude. Connections drag: **PASS**; the persisted order became Codex/Copilot/Claude and survived process restarts.
- Haptic setting: both enabled and disabled values survived process termination/restart; the toggle was returned to enabled for the demo. Unit tests verify disabled silence, enabling without a stale throttle and coalescing simultaneous events.
- The last locale label fix was installed in the emulator; Japanese label verification is recorded with the final screenshot.
- Physical-device vibration strength, manufacturer-specific feel and a new production release were **not** tested/published in this task. The emulator cannot establish the physical feel of haptics.

Native artifacts were kept outside Git: `continuous-pager-demo.mp4`, `continuous-pager-half.png`, `connections-after-drag.png`, `trends-after-drag.png`, `haptic-feedback-setting.png` and `haptic-feedback-setting-ja.png`. They use demo data, not the user's private GitHub content.

## Atomic commits

| Commit | Subject | Purpose |
| --- | --- | --- |
| `3195ae7` | `refactor(ui): share a pinned header above paged content` | Keep one glass header pinned above the moving page bodies. |
| `b6d8133` | `fix(navigation): keep tab pages on a continuous scroll track` | Replace destination-entry slides with a continuous tab track and retain the Settings origin. |
| `c45884b` | `feat(preferences): persist independent provider orders per page` | Save independent Usage, Trends and Connections provider orders without changing the legacy Usage key. |
| `746688e` | `feat(connections): reorder providers with the shared drag controls` | Add the existing Reorder/Done and long-press controls to the provider directory. |
| `3cbebd0` | `feat(trends): reorder service panels across history and spend` | Reorder History/Spend service panels with a shared Trends order and a connected internal track. |
| `b428671` | `fix(navigation): track pager progress in the glass tab lens` | Follow fractional page progress in the bar lens and allow selected-tab releases to close Settings. |
| `8fd274b` | `test(ui): cover active-page refresh and collapsed reorder rows` | Update the existing source assertions for active-page refresh and collapsed editing rows. |
| `1af59f6` | `fix(gestures): isolate nested controls from page navigation` | Keep chart/filter gestures inside their controls, isolate programmatic panes and preserve the drag grip. |
| `3935b78` | `fix(connections): retain Windows panels when the list scrolls` | Keep expanded Windows connection panels when their lazy item leaves the viewport. |
| `bb11c07` | `feat(haptics): add optional light feedback to glass controls` | Add a persisted haptic switch, one light pulse on lens lift and shared feedback gating/throttling. |
| `3a43f99` | `fix(settings): localize the haptic switch accessibility label` | Use locale-aware text for the new setting and its accessibility label. |

The final documentation commit records this contract and verification script; its SHA is reported in the completion message. No branch was pushed, no PR was created, and no merge/tag/release was performed for this new task.
