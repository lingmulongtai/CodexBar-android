#!/usr/bin/env python3
"""Check that real Glass screens stop rendering at rest, including after a scroll.

Requires an installed debug APK and a disposable emulator. Uses synthetic data only.
"""

import argparse
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--serial", required=True)
parser.add_argument("--adb", default="adb")
parser.add_argument("--screenshots", type=Path)
parser.add_argument("--dark", action="store_true", help="Verify the dark Liquid Glass appearance")
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
    # A stopped process or a system ANR dialog can also render zero frames. Require the real app
    # to be the visible surface, and never accept a stale hierarchy after a failed dump.
    for _ in range(5):
        dumped = adb("shell", "uiautomator", "dump", "/sdcard/glass-idle.xml")
        if "dumped to" in dumped:
            root = ET.fromstring(adb("shell", "cat", "/sdcard/glass-idle.xml"))
            nodes = list(root.iter("node"))
            assert any(node.get("package") == package for node in nodes), f"{label}: app is obscured"
            assert not any("isn't responding" in node.get("text", "") or "keeps stopping" in node.get("text", "") for node in nodes), f"{label}: error dialog"
            break
        time.sleep(0.5)
    else:
        raise AssertionError(f"{label}: no fresh UI hierarchy")
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
        "--ez", "dark_theme", str(args.dark).lower(),
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
