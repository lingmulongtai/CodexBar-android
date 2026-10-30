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
parser.add_argument("--locale", choices=("en", "ja"), default="en")
parser.add_argument("--edge", action="store_true", help="Also hold a dragged card near the bottom to verify scrolling")
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
labels = {
    "en": ("Reorder", "Arrange services", "Hold a card to move it · Saved automatically", "Use automatic order", "Done"),
    "ja": ("並び替え", "並び替え中", "長押しで移動・順番は自動保存", "自動の並び順に戻す", "完了"),
}
reorder, editing, hint, automatic, done = labels[args.locale]
adb("shell", "cmd", "locale", "set-app-locales", package, "--locales", args.locale)
adb("shell", "am", "start", "-W", "-f", "0x14000000", "-n", f"{package}/.debug.ScreenshotActivity",
    "--es", "theme", args.theme, "--es", "destination", "dashboard", "--ez", "analytics", "true",
    "--ez", "dark_theme", str(args.dark).lower())
time.sleep(5)
root = tree()
assert top(root, "Codex") < top(root, "Cline")
tap(find(root, reorder))
root = tree()
find(root, editing)
find(root, hint)
if args.screenshots:
    args.screenshots.mkdir(parents=True, exist_ok=True)
    (args.screenshots / "editing.png").write_bytes(subprocess.check_output([args.adb, "-s", args.serial, "exec-out", "screencap", "-p"]))

first = centre(find(root, "Codex"))
second = centre(find(root, "Cline"))
destination = (first[0], second[1] + (second[1] - first[1]) * 3 // 5)
print("Drag", first, "toward", destination, flush=True)
adb("shell", "input", "touchscreen", "motionevent", "DOWN", *first)
time.sleep(0.9)
for step in range(1, 11):
    point = tuple(round(a + (b - a) * step / 10) for a, b in zip(first, destination))
    adb("shell", "input", "touchscreen", "motionevent", "MOVE", *point)
    time.sleep(0.06)
adb("shell", "input", "touchscreen", "motionevent", "UP", *destination)
root = tree()
print("Positions", top(root, "Cline"), top(root, "Codex"), flush=True)
assert top(root, "Cline") < top(root, "Codex"), "drag did not move Codex below Cline"
tap(find(root, automatic))
root = tree()
assert top(root, "Codex") < top(root, "Cline"), "automatic order was not restored"
tap(find(root, done))
root = tree()
find(root, reorder)
assert not any(n.get("text") == editing for n in root.iter("node"))
tap(find(root, reorder))
find(tree(), editing)
adb("shell", "input", "keyevent", "BACK")
find(tree(), reorder)
if args.edge:
    tap(find(tree(), reorder))
    root = tree()
    first = centre(find(root, "Codex"))
    before = top(root, "Cline")
    size = re.findall(r"(\d+)x(\d+)", adb("shell", "wm", "size"))[-1]
    destination = (first[0], int(size[1]) - 40)
    adb("shell", "input", "touchscreen", "motionevent", "DOWN", *first)
    try:
        time.sleep(0.9)
        for step in range(1, 11):
            point = tuple(round(a + (b - a) * step / 10) for a, b in zip(first, destination))
            adb("shell", "input", "touchscreen", "motionevent", "MOVE", *point)
            time.sleep(0.06)
        time.sleep(1.5)
    finally:
        adb("shell", "input", "touchscreen", "motionevent", "UP", *destination)
    root = tree()
    cline = [n for n in root.iter("node") if n.get("text") == "Cline"]
    assert not cline or centre(cline[0])[1] < before - 50, "dragging near the edge did not scroll"
    tap(find(root, done))
    root = tree()
    assert not any(n.get("text") == done for n in root.iter("node")), "pinned Done did not finish editing"
    print("PASS edge scrolling")
print("PASS inline reorder, automatic reset, Done, and Back")
