# v0.16.2 UI evidence

These six native Android API 36.1 emulator captures show the continuous-pager implementation that is included in v0.16.2. They were captured on 2026-11-03 before the release metadata bump, with 540 × 1200 geometry and synthetic Usage/GitHub data. The application source at capture was the completed continuous-pager branch, not an image mockup. No account credentials or private repository data are shown.

- `continuous-pager-half.png`: two adjacent pages held between destinations by a real pointer gesture; the header remains pinned.
- `connections-after-drag.png`: provider ordering in Connections, saved independently of Usage.
- `trends-reorder.png`: the Reorder/Done controls in History.
- `trends-after-drag.png`: Codex moved below other providers by long-press dragging.
- `haptic-feedback-setting-ja.png`: the persisted haptic switch and Japanese label.
- `github-return-back.png`: GitHub restored after opening Settings and pressing Back.

Screenshots cannot demonstrate physical vibration strength. The debug screenshot activity and fixtures are excluded from the signed production APK.

The seventh image, `stable-v0161-app-info.png`, is the production v0.16.1 APK freshly downloaded from the public Release and installed on a disposable emulator on 2026-11-04. Android Settings displays the approved blue icon. It is an icon-audit reference, not a screenshot of the v0.16.2 app screen.
