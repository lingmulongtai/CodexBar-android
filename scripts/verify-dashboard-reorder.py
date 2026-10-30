#!/usr/bin/env python3
"""Exercise inline dashboard ordering with synthetic data on a disposable emulator."""

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
parser.add_argument("--theme", choices=("LIQUID_GLASS", "MATERIAL_3"), default="LIQUID_GLASS")
parser.add_argument("--dark", action="store_true")
args = parser.parse_args()
package = "com.codexbar.android"


def adb(*parts):
    return subprocess.check_output([args.adb, "-s", args.serial, *map(str, parts)], text=True, encoding="utf-8").strip()


def tree():
    for _ in range(5):
        result = adb("shell", "uiautomator", "dump", "/sdcard/dashboard-reorder.xml")
        if "dumped to" in result:
            root = ET.fromstring(adb("shell", "cat", "/sdcard/dashboard-reorder.xml"))
            assert not any("isn't responding" in n.get("text", "") or "keeps stopping" in n.get("text", "") for n in root.iter("node")), "error dialog"
            return root
        time.sleep(0.5)
    raise AssertionError("Could not read the dashboard")


def find(root, label):
    return next(n for n in root.iter("node") if n.get("text") == label or n.get("content-desc") == label)


def centre(node):
    left, top, right, bottom = map(int, re.findall(r"\d+", node.get("bounds")))
    return (left + right) // 2, (top + bottom) // 2


def tap(node):
    adb("shell", "input", "tap", *centre(node))


def top(root, label):
    return centre(find(root, label))[1]


assert adb("shell", "getprop", "ro.kernel.qemu") == "1", "Use a disposable emulator"
adb("shell", "cmd", "locale", "set-app-locales", package, "--locales", "en")
adb("shell", "am", "start", "-W", "-f", "0x14000000", "-n", f"{package}/.debug.ScreenshotActivity",
    "--es", "theme", args.theme, "--es", "destination", "dashboard", "--ez", "analytics", "true",
    "--ez", "dark_theme", str(args.dark).lower())
time.sleep(5)
root = tree()
assert top(root, "Codex") < top(root, "Cline")
tap(find(root, "Reorder"))
root = tree()
find(root, "Arrange services")
find(root, "Touch and hold a card to move it. Your order is saved automatically.")
if args.screenshots:
    args.screenshots.mkdir(parents=True, exist_ok=True)
    (args.screenshots / "editing.png").write_bytes(subprocess.check_output([args.adb, "-s", args.serial, "exec-out", "screencap", "-p"]))

first = centre(find(root, "Codex"))
second = centre(find(root, "Cline"))
destination = (first[0], second[1] + (second[1] - first[1]) // 2)
adb("shell", "input", "touchscreen", "draganddrop", *first, *destination, 1800)
root = tree()
assert top(root, "Cline") < top(root, "Codex"), "drag did not move Codex below Cline"
tap(find(root, "Use automatic order"))
root = tree()
assert top(root, "Codex") < top(root, "Cline"), "automatic order was not restored"
tap(find(root, "Done"))
root = tree()
find(root, "Reorder")
assert not any(n.get("text") == "Arrange services" for n in root.iter("node"))
tap(find(root, "Reorder"))
find(tree(), "Arrange services")
adb("shell", "input", "keyevent", "BACK")
find(tree(), "Reorder")
print("PASS inline reorder, automatic reset, Done, and Back")
