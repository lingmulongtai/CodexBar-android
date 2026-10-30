#!/usr/bin/env python3
"""Exercise real held gestures, cancellation, disabled input and RTL on a disposable emulator."""

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
args = parser.parse_args()
package = "com.codexbar.android"


def adb(*parts):
    return subprocess.check_output([args.adb, "-s", args.serial, *map(str, parts)], text=True, encoding="utf-8").strip()


def tree():
    for _ in range(5):
        result = adb("shell", "uiautomator", "dump", "/sdcard/glass-controls.xml")
        if "dumped to" in result:
            root = ET.fromstring(adb("shell", "cat", "/sdcard/glass-controls.xml"))
            assert not any("isn’t responding" in entry.get("text", "") or "isn't responding" in entry.get("text", "") for entry in root.iter("node")), "ANR dialog"
            return root
        time.sleep(0.5)
    raise AssertionError("Could not read a fresh UI hierarchy")


def node(label, root=None):
    root = root if root is not None else tree()
    for entry in root.iter("node"):
        if entry.get("content-desc") == label:
            # A caller's description may be a separate semantics wrapper around the toggle.
            return next((candidate for candidate in root.iter("node")
                         if candidate.get("bounds") == entry.get("bounds") and candidate.get("checkable") == "true"), entry)
        if (entry.get("clickable") == "true" or entry.get("selected") == "true") and any(child.get("text") == label for child in entry.iter("node")):
            return entry
    raise AssertionError(f"Missing control: {label}")


def bounds(entry):
    return list(map(int, re.findall(r"\d+", entry.get("bounds"))))


def centre(entry):
    left, top, right, bottom = bounds(entry)
    return (left + right) // 2, (top + bottom) // 2


def text_exists(root, value):
    assert any(entry.get("text") == value for entry in root.iter("node")), value


def held_drag(start, end):
    adb("shell", "input", "touchscreen", "draganddrop", *start, *end, 600)


def held_cancel(start, end):
    adb("shell", "input", "touchscreen", "motionevent", "DOWN", *start)
    time.sleep(0.8)
    adb("shell", "input", "touchscreen", "motionevent", "MOVE", *end)
    time.sleep(0.3)
    adb("shell", "input", "touchscreen", "motionevent", "CANCEL", *end)


assert adb("shell", "getprop", "ro.kernel.qemu") == "1", "Use a disposable emulator"
adb("shell", "input", "keyevent", 224)
adb("shell", "input", "keyevent", 82)
adb("logcat", "-b", "crash", "-c")
if args.screenshots:
    args.screenshots.mkdir(parents=True, exist_ok=True)

for rtl in (False, True):
    adb("shell", "am", "start", "-W", "-f", "0x14000000", "-n", f"{package}/.debug.GlassControlsActivity", "--ez", "rtl", str(rtl).lower())
    time.sleep(1)
    root = tree()
    text_exists(root, "Tab commits: 0")
    first, second, third = [centre(node(label, root)) for label in ("First", "Second", "Third")]
    assert (first[0] > third[0]) == rtl
    held_drag(first, third)
    root = tree()
    assert node("Third", root).get("selected") == "true"
    text_exists(root, "Tab commits: 1")
    adb("shell", "input", "tap", *second)
    root = tree()
    assert node("Second", root).get("selected") == "true"
    text_exists(root, "Tab commits: 2")
    held_cancel(second, first)
    root = tree()
    assert node("Second", root).get("selected") == "true"
    text_exists(root, "Tab commits: 2")
    switch = node("Interactive switch", root)
    assert switch.get("checked") == "true"
    x, y = centre(switch)
    width = bounds(switch)[2] - bounds(switch)[0]
    off = (x + int(width * 0.75) * (1 if rtl else -1), y)
    held_drag((x, y), off)
    root = tree()
    assert node("Interactive switch", root).get("checked") == "false"
    text_exists(root, "Switch commits: 1")
    held_cancel((x, y), (x + int(width * 0.75) * (-1 if rtl else 1), y))
    root = tree()
    assert node("Interactive switch", root).get("checked") == "false"
    text_exists(root, "Switch commits: 1")
    held_drag(centre(node("Disabled switch", root)), off)
    root = tree()
    assert node("Disabled switch", root).get("checked") == "true"
    text_exists(root, "Switch commits: 1")
    adb("shell", "input", "tap", *centre(node("Interactive switch", root)))
    root = tree()
    assert node("Interactive switch", root).get("checked") == "true"
    text_exists(root, "Switch commits: 2")
    slider = node("Test slider", root)
    left, top, right, bottom = bounds(slider)
    held_drag(centre(slider), (left + 2 if rtl else right - 2, (top + bottom) // 2))
    root = tree()
    text_exists(root, "Slider value: 100")
    if args.screenshots:
        target = args.screenshots / ("glass-controls-rtl.png" if rtl else "glass-controls-ltr.png")
        target.write_bytes(subprocess.check_output([args.adb, "-s", args.serial, "exec-out", "screencap", "-p"]))
    print(f"PASS {'RTL' if rtl else 'LTR'}: held tabs/switch/slider, tap, cancellation, single commit, disabled switch", flush=True)

assert f"Process: {package}" not in adb("logcat", "-b", "crash", "-d"), "App crashed"
