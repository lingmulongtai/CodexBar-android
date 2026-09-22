"""Exercise real RemoteViews through AppWidgetHost on a disposable Android emulator."""
import argparse
import json
import subprocess
import time
import sys

sys.stdout.reconfigure(encoding="utf-8")

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--adb", default="adb")
parser.add_argument("--serial", required=True)
args = parser.parse_args()

def adb(*parts, check=True):
    return subprocess.run([args.adb, "-s", args.serial, *parts], capture_output=True,
                          text=True, encoding="utf-8", check=check).stdout.strip()

assert adb("shell", "getprop", "ro.kernel.qemu") == "1", "Demo tests require an emulator"
adb("shell", "appwidget", "grantbind", "--package", "com.codexbar.android", "--user", "0")
for width, height, legacy, redacted in [
    (320, 40, False, False), (320, 48, False, False),
    (320, 60, False, False), (320, 80, False, False),
    (320, 120, False, False), (140, 60, False, False),
    (320, 260, True, False), (320, 60, True, False),
    (320, 60, False, True), (320, 60, False, False),
]:
    adb("shell", "am", "force-stop", "com.codexbar.android")
    adb("shell", "run-as", "com.codexbar.android", "rm", "-f", "files/widget-host-result.json")
    adb("shell", "am", "start", "-W", "-n", "com.codexbar.android/.debug.WidgetHostActivity",
        "--ez", "seed_demo", "true", "--ei", "width_dp", str(width), "--ei", "height_dp", str(height),
        "--ez", "legacy_sizes", str(legacy).lower(), "--ez", "redacted", str(redacted).lower())
    result = {}
    deadline = time.monotonic() + 20
    while time.monotonic() < deadline:
        time.sleep(0.5)
        raw = adb("shell", "run-as", "com.codexbar.android", "cat", "files/widget-host-result.json", check=False)
        if not raw:
            continue
        result = json.loads(raw)
        text = " | ".join(result["visible"])
        if redacted:
            passed = "Quota hidden" in text and "%" not in "".join(result["text"]) and "Basic view" not in text
        else:
            passed = "Codex" in text and "62% left" in text and "Basic view" not in text
            if 56 <= height < 180:
                passed = passed and "Copilot" in text and "74% left" in text
        if passed:
            break
    else:
        raise AssertionError(f"Host rendering failed at {width}x{height}, legacy={legacy}, redacted={redacted}: {result}")
    print(f"PASS {width}x{height} legacy={legacy} redacted={redacted}: {text}", flush=True)
