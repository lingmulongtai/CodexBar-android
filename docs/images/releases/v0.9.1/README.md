# v0.9.1 verification captures

Captured on 2026-09-22 from the v0.9.1 debug APK, Android 16.1 (API 36.1),
1080 x 2400 pixels at 420 dpi, font scale 1.0. All six PNGs are fresh captures
of actual Android views and were visually inspected. Only demo quotas were used;
no accounts or credentials were added. Debug activities and the pin receiver are
excluded from the release build.

| Capture | What was exercised |
| --- | --- |
| `niagara-thin-widget.png` | Niagara Launcher v1.16.29, production widget pinned and resized using Niagara controls; reported content size 315 x 56dp |
| `widget-40dp.png` | Real AppWidgetHost, 320 x 40dp, one fully visible quota row |
| `widget-140x60dp.png` | Real AppWidgetHost, 140 x 60dp, two fully visible quota rows |
| `widget-diagnostics.png` | Production settings for the Niagara widget, with stage/time/size; no linked accounts in this demo environment |
| `dashboard-material3.png` | Current dashboard rendered by ScreenshotActivity |
| `live-update.png` | Production Android 16 notification renderer after a hide/show privacy cycle |

## Reproduction

Install the debug APK on a disposable emulator. Never run the demo harness on a
device containing real account data: it overwrites widget cache and privacy settings.

```sh
python scripts/verify-widget-host.py --adb /path/to/adb --serial emulator-5554
```

The script checks ten size/privacy cases, including legacy hosts without
`OPTION_APPWIDGET_SIZES`, across app process restarts. It checks fully visible
text and rejects the basic fallback when a full render is expected.

To capture a particular size:

```sh
adb shell am start -W -S -n com.codexbar.android/.debug.WidgetHostActivity \
  --ez seed_demo true --ei width_dp 320 --ei height_dp 40
adb shell screencap -p /sdcard/capture.png
adb pull /sdcard/capture.png widget-40dp.png
```

After seeding the demo cache, install Niagara from its
[official release](https://github.com/NiagaraLauncher/Niagara-Issues/releases/tag/v1.16.29),
complete its onboarding and select it as the emulator's home app. Request the widget:

```sh
adb shell am start -W -n com.codexbar.android/.debug.WidgetHostActivity \
  --ez seed_demo true --ez pin_widget true
```

Accept the launcher and Android pin dialogs, then long-press the widget and use
**Move and resize**. This exercises the production receiver, composition, and
RemoteViews in Niagara. The debug callback supplies demo provider selection only.

Notification checks use actual NotificationManager entries:

```sh
adb shell pm grant com.codexbar.android android.permission.POST_NOTIFICATIONS
adb shell am start -W -S -n com.codexbar.android/.debug.ScreenshotActivity \
  --es theme MATERIAL_3 --ez notification true --ez lock_screen_redacted false \
  --ez verify_privacy_toggle true
adb logcat -d -s CodexBarNotificationTest:I '*:S'
adb shell cmd statusbar expand-notifications
```

Repeat with `--ez lock_screen_redacted true` to verify PRIVATE visibility and a
generic public version. Wi-Fi and mobile data were also disabled for a 40dp
widget render, then restored; the cached 62% remaining label stayed visible.

These checks do not reproduce Samsung One UI's Now Bar, fingerprint unlocking,
large accessibility fonts, or every physical-device launcher configuration.
