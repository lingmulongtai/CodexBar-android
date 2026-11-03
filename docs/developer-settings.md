# App-owned motion and glass tuning

Open **Settings → Dev settings**. All values are local appearance preferences in the existing settings DataStore. The default animation speed is **1×**, independent of Android's animator-duration scale, including Android scale 0. Choose **0.25–3×** with the smooth slider or use the **0.5× / 1× / 2×** presets. Larger speed values mean faster playback. Language-selection settling time follows the same app speed.

The process installs one lifecycle-aware Compose window recomposer policy with an app-owned `MotionDurationScale`. Main, dialogs, widget configuration and debug previews therefore use the same clock policy. Lifecycle pausing/frame scheduling remain Compose-owned. System permission prompts, launcher/window animations and external apps are still platform-owned. `WindowRecomposerPolicy` is an internal Compose UI API; its use is isolated in `DeveloperSettingsRuntime` and must be revalidated when upgrading the pinned Compose version. See the [MotionDurationScale API](https://developer.android.com/reference/kotlin/androidx/compose/ui/MotionDurationScale) and the matching Compose `WindowRecomposer.android.kt` source.

## Glass groups

- **Cards:** Running out first, provider cards, passive settings/GitHub panels and the service detail sheet.
- **Header:** the black-to-clear page header; its established gradient and geometry remain in place.
- **Tab bar:** its base, refresh action and moving selection lens.
- **Controls:** switches, sliders, segmented selections, actions and text fields.

| Parameter | Bounds | Meaning |
| --- | --- | --- |
| Frost | 0–40 dp | Native backdrop blur radius |
| Bend | 0–2× | Multiplies existing component refraction, preserving zero/rest and pressed-only behavior |
| Dispersion | 0–1 | Chromatic split inside an active lens |
| Rim | 0–1 | Border highlight opacity |
| Edge shine | 0–1 | Shader edge gleam, separate from the border |
| Saturation | 0–2 | Backdrop saturation inside an active lens |
| Tint opacity | 0–1 | Material fill opacity |
| Bend reach | 0.25–2× | Multiplies the existing reach (including the header's original reach of 4) |

Every field starts as **inherited**, shown as Default. This preserves each component's exact original material (including 12 dp clickable vs 16 dp passive cards, light/dark values and pressed states). A slider creates an explicit override for its group. **Reset this group** removes that group's overrides; **Reset all Dev settings** also restores animation speed to 1×. Neither changes connections or account data.

Changes preview immediately and persist at gesture completion. Preview ownership, saved data and outstanding writes are separate: an older write cannot replace a newer slider draft, leaving cancels only an uncommitted draft, and process-owned writes survive leaving the screen. Untouched editors accept a late initial disk read. Save failure remains visible and offers retry. Corrupt/non-finite saved parameters fall back or normalize into bounded values.

Tuning never changes dimensions, shape, corner radii, hit targets or shadow geometry. Nested panels keep their existing translucent fallback without re-sampling clearer wallpaper. Refraction requires Android 13+, blur Android 12+; older/fallback surfaces retain their existing rendering path. Some shader properties have no effect when bend is zero or a surface has no live backdrop.

## Verification

Read-only upstream main checked at `1811d1fe032fd4a80240fd6bc5d04cee649a0afb`; no upstream-only commits to integrate or defer. Work starts from released main `cb8a827` (v0.15.0). The production signer, acquisition modes, connections and release workflow are unchanged.

A disposable API 36.1 emulator measured a 400 ms Compose tween with the production recomposer policy:

| Android animator scale | App speed | Measured time |
| --- | --- | --- |
| 0× | 1× | 421 ms |
| 1× | 1× | 446 ms |
| 10× | 1× | 447 ms |
| 10× | 2× | 242 ms |
| 0× | 0.5× | 879 ms |
| 1×, after process restart | 0.5× | 824 ms |

Frame/startup scheduling adds normal emulator overhead. Every run checked the actual coroutine context and a child dialog; saved frost 24 dp and the selected speed survived process restart. The debug-only `DeveloperMotionProofActivity` requires explicit `seed_demo=true`, and is excluded from release builds. No physical-device verification is claimed for this iteration.

Final checks: 500 tests, 498 passed and two explicit opt-in native Windows interoperability checks skipped; debug lint and APK assembly passed. The actual Dev UI preset and a fast slider tap were also exercised: app speed 2× and frost about 8.19 dp persisted after process restart, with a measured 230 ms tween and the same duration scale in a child dialog.
