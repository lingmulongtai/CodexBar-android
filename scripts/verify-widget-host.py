"""Exercise real RemoteViews through AppWidgetHost on a disposable Android emulator."""
import argparse
import json
import subprocess
import time
import sys
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--adb", default="adb")
parser.add_argument("--serial", required=True)
parser.add_argument("--screenshots", type=Path, help="Save fresh template screenshots to this directory")
args = parser.parse_args()
if args.screenshots:
    args.screenshots.mkdir(parents=True, exist_ok=True)

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
            passed = "Codex" in result["visible"] and "62%" in result["visible"]
            if 56 <= height < 180:
                passed = passed and "Copilot" in result["visible"] and "74%" in result["visible"]
        if passed:
            break
    else:
        raise AssertionError(f"Host rendering failed at {width}x{height}, legacy={legacy}, redacted={redacted}: {result}")
    print(f"PASS {width}x{height} legacy={legacy} redacted={redacted}: {text}", flush=True)

# Every selectable template must retain all three providers at the user's Niagara size.
for template in ["LEDGER", "METERS", "COLUMNS", "TILES", "RINGS", "SEGMENTS", "VERTICAL", "FOCUS", "DUAL", "RESET"]:
    adb("shell", "am", "force-stop", "com.codexbar.android")
    adb("shell", "run-as", "com.codexbar.android", "rm", "-f", "files/widget-host-result.json")
    adb("shell", "am", "start", "-W", "-n", "com.codexbar.android/.debug.WidgetHostActivity",
        "--ez", "seed_demo", "true", "--ei", "width_dp", "347", "--ei", "height_dp", "69",
        "--ez", "three_services", "true", "--es", "template", template)
    deadline = time.monotonic() + 20
    result = {}
    while time.monotonic() < deadline:
        time.sleep(.5)
        raw = adb("shell", "run-as", "com.codexbar.android", "cat", "files/widget-host-result.json", check=False)
        if not raw:
            continue
        result = json.loads(raw)
        text = " | ".join(result["visible"])
        if all(value in text for value in ["Codex", "Copilot", "Claude", "62%", "74%", "56%", "↻"]):
            break
    else:
        raise AssertionError(f"Template {template} clipped or omitted data: {result}")
    print(f"PASS template={template} 347x69: {text}", flush=True)
    if args.screenshots:
        with (args.screenshots / f"widget-{template.lower()}.png").open("wb") as output:
            subprocess.run([args.adb, "-s", args.serial, "exec-out", "screencap", "-p"], stdout=output, check=True)
