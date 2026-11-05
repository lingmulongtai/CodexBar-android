#!/usr/bin/env python3
"""Check pinned headers and Settings navigation with the tablet keyboard visible."""
import argparse
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--adb', default='adb')
parser.add_argument('--adb-port', default='5037')
parser.add_argument('--serial', required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
base = [args.adb, '-P', args.adb_port, '-s', args.serial]
package = 'com.codexbar.android'

def adb(*parts):
    return subprocess.check_output(base + list(map(str, parts)), text=True, encoding='utf-8', timeout=30).strip()

def bounds(node):
    return list(map(int, re.findall(r'-?\d+', node.get('bounds'))))

def tree():
    for _ in range(6):
        if 'dumped to' not in adb('shell', 'uiautomator', 'dump', '/sdcard/codexbar-ime.xml'):
            time.sleep(.5)
            continue
        root = ET.fromstring(adb('shell', 'cat', '/sdcard/codexbar-ime.xml'))
        assert not any(n.get('resource-id') in ('android:id/aerr_close', 'android:id/aerr_wait') for n in root.iter('node'))
        assert 'am_anr' not in adb('logcat', '-b', 'events', '-d', '-s', 'am_anr:V')
        return root
    raise AssertionError('Could not read fresh UI')

def find(root, label):
    return [n for n in root.iter('node') if (n.get('text') == label or n.get('content-desc') == label)
            and bounds(n)[2] > bounds(n)[0] and bounds(n)[3] > bounds(n)[1]]

def tap(node):
    left, top, right, bottom = bounds(node)
    adb('shell', 'input', 'tap', (left+right)//2, (top+bottom)//2)
    time.sleep(1)

def capture(name):
    data = subprocess.check_output(base + ['exec-out', 'screencap', '-p'], timeout=30)
    start = data.find(b'\x89PNG\r\n\x1a\n')
    assert start >= 0
    (args.output / (name+'.png')).write_bytes(data[start:])
    (args.output / (name+'.xml')).write_text(ET.tostring(tree(), encoding='unicode'), encoding='utf-8')

assert adb('shell', 'getprop', 'ro.kernel.qemu') == '1', 'Use a disposable emulator'
assert 'CodexBar' in adb('emu', 'avd', 'name'), 'Use a dedicated CodexBar fixture'
args.output.mkdir(parents=True, exist_ok=True)
adb('shell', 'wm', 'size', '1280x800')
adb('shell', 'wm', 'density', '160')
adb('shell', 'settings', 'put', 'system', 'font_scale', '1')
adb('shell', 'cmd', 'locale', 'set-app-locales', package, '--locales', 'en')
adb('logcat', '-b', 'events', '-c')
adb('shell', 'am', 'start', '-S', '-W', '-n', package+'/.debug.ScreenshotActivity',
    '--es', 'theme', 'LIQUID_GLASS', '--es', 'destination', 'connections',
    '--ez', 'dark_theme', 'true', '--ez', 'github_demo', 'true')
tap(find(tree(), 'Find a provider')[0])
adb('shell', 'input', 'text', 'Codex')
time.sleep(1)
assert 'mInputShown=true' in adb('shell', 'dumpsys', 'input_method'), 'Keyboard did not open'
root = tree()
gears = find(root, 'Settings')
assert gears and bounds(gears[0])[1] < 160, 'Keyboard panned the pinned header out of the window'
assert not find(root, 'Usage'), 'Floating navigation covered the keyboard input'
capture('tablet-keyboard-header')
tap(min(gears, key=lambda n: bounds(n)[1]))
root = tree()
assert find(root, 'Settings') and find(root, 'Usage'), 'Settings did not restore navigation'
assert 'mInputShown=true' not in adb('shell', 'dumpsys', 'input_method'), 'Settings retained the keyboard'
capture('tablet-settings-after-keyboard')
adb('shell', 'input', 'keyevent', 'BACK')
time.sleep(1)
root = tree()
assert find(root, 'Connections') and any(n.get('class') == 'android.widget.EditText' and
    n.get('text') == 'Codex' for n in root.iter('node')), 'Returning lost the search draft'
tap(find(root, 'Clear provider search')[0])
print('PASS tablet IME, pinned header, Settings focus dismissal, navigation restoration and retained search', flush=True)
