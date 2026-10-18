#!/usr/bin/env python3
"""Check that real Glass screens stop rendering at rest, including after a scroll.

Requires an installed debug APK and a disposable emulator. Uses synthetic data only.
"""

import argparse
from pathlib import Path
import re
import subprocess
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--serial", required=True)
parser.add_argument("--adb", default="adb")
parser.add_argument("--screenshots", type=Path)
args = parser.parse_args()
package = "com.codexbar.android"


def adb(*parts):
    return subprocess.run(
        [args.adb, "-s", args.serial, *parts], check=True,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
        encoding="utf-8", errors="replace",
    ).stdout.strip()


def assert_idle(label):
    adb("shell", "dumpsys", "gfxinfo", package, "reset")
    time.sleep(5)
    stats = adb("shell", "dumpsys", "gfxinfo", package)
    match = re.search(r"Total frames rendered: (\d+)", stats)
    assert match, f"No frame statistics for {label}"
    frames = int(match.group(1))
    # Allow occasional label/inset updates, but reject a sustained animation/draw loop.
    assert frames <= 8, f"{label}: {frames} frames in 5 idle seconds (expected <=8)"
    assert adb("shell", "pidof", package), f"{label}: app exited"
    print(f"PASS {label}: {frames} frames in 5 idle seconds", flush=True)


assert adb("shell", "getprop", "ro.kernel.qemu") == "1", "Use a disposable emulator"
adb("shell", "input", "keyevent", "224")
adb("shell", "input", "keyevent", "82")
adb("logcat", "-b", "crash", "-c")
if args.screenshots:
    args.screenshots.mkdir(parents=True, exist_ok=True)

for page in ("dashboard", "spend"):
    adb("shell", "am", "force-stop", package)
    started = adb(
        "shell", "am", "start", "-W", "-n", f"{package}/.debug.ScreenshotActivity",
        "--es", "theme", "LIQUID_GLASS", "--es", "destination", page,
        "--ez", "analytics", "true",
    )
    assert "Status: ok" in started and "Error" not in started, started
    time.sleep(2)
    assert_idle(f"{page} initial")
    adb("shell", "input", "swipe", "200", "600", "200", "200", "450")
    time.sleep(2)
    assert_idle(f"{page} after scroll")
    if args.screenshots:
        with (args.screenshots / f"glass-{page}-scrolled.png").open("wb") as output:
            subprocess.run(
                [args.adb, "-s", args.serial, "exec-out", "screencap", "-p"],
                check=True, stdout=output,
            )

assert f"Process: {package}" not in adb("logcat", "-b", "crash", "-d"), "App crashed"
