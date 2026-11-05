#!/usr/bin/env python3
"""Capture and check real adaptive UI on a disposable emulator with synthetic data."""
import argparse
import json
from pathlib import Path
import re
import struct
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--adb', default='adb')
parser.add_argument('--adb-port', default='5037')
parser.add_argument('--serial', required=True)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--case', action='append', help='width,height in dp; repeat for a matrix')
parser.add_argument('--locale', choices=['en', 'ja'], default='en')
parser.add_argument('--font-scale', type=float, default=1.0)
parser.add_argument('--density', type=int, default=160)
parser.add_argument('--baseline', action='store_true', help='Capture known old geometry without the regression assertion')
parser.add_argument('--capture-only', action='store_true', help='Capture explicit demo destinations without UI Automation; requires visual review')
parser.add_argument('--light', action='store_true', help='Capture the light theme')
args = parser.parse_args()
base = [args.adb, '-P', args.adb_port, '-s', args.serial]
package = 'com.codexbar.android'

def adb(*parts):
    return subprocess.check_output(base + list(map(str, parts)), text=True, encoding='utf-8', timeout=25).strip()

def tree():
    for _ in range(5):
        result = adb('shell', 'uiautomator', 'dump', '/sdcard/codexbar-adaptive.xml')
        if 'dumped to' not in result:
            time.sleep(1)
            continue
        root = ET.fromstring(adb('shell', 'cat', '/sdcard/codexbar-adaptive.xml'))
        (args.output / 'last-ui.xml').write_text(ET.tostring(root, encoding='unicode'), encoding='utf-8')
        if any(n.get('package') == package for n in root.iter('node')):
            return root
        time.sleep(1)
    raise AssertionError('App is not visible')

def bounds(node):
    return list(map(int, re.findall(r'-?\d+', node.get('bounds'))))

def find(root, text, description=False):
    return [n for n in root.iter('node') if (n.get('content-desc') == text or
            (not description and n.get('text') == text)) and
            bounds(n)[2] > bounds(n)[0] and bounds(n)[3] > bounds(n)[1]]

def tap(text, description=False):
    nodes = find(tree(), text, description)
    assert nodes, f'Missing action: {text}'
    node = max(nodes, key=lambda n: bounds(n)[1])
    left, top, right, bottom = bounds(node)
    adb('shell', 'input', 'tap', (left + right)//2, (top + bottom)//2)
    time.sleep(.8)

def capture(name):
    data = subprocess.check_output(base + ['exec-out', 'screencap', '-p'], timeout=25)
    # Foldable AVDs can prefix a multiple-display warning on stdout.
    start = data.find(b'\x89PNG\r\n\x1a\n')
    assert start >= 0, 'Emulator did not return a PNG screenshot'
    data = data[start:]
    target = args.output / (name + '.png')
    target.write_bytes(data)
    return struct.unpack('>II', data[16:24])

def labels():
    resources = Path(__file__).resolve().parents[1] / 'app/src/main/res'
    values = {n.get('name'): ''.join(n.itertext()) for n in ET.parse(resources / 'values/strings.xml').getroot() if n.tag == 'string'}
    if args.locale == 'ja':
        values.update({n.get('name'): ''.join(n.itertext()) for n in ET.parse(resources / 'values-ja/strings.xml').getroot() if n.tag == 'string'})
    return values

assert adb('shell', 'getprop', 'ro.kernel.qemu') == '1', 'Disposable emulator required'
assert 'CodexBar' in adb('emu', 'avd', 'name'), 'Use a dedicated CodexBar emulator'
args.output.mkdir(parents=True, exist_ok=True)
def failure_artifact(kind, error, traceback):
    (args.output / 'failure.txt').write_text(f'{kind.__name__}: {error}\n', encoding='utf-8')
    try:
        capture('failure')
        runtime = adb('logcat', '-d', '-t', '120', '-s', 'AndroidRuntime')
        (args.output / 'failure-runtime.txt').write_text(runtime, encoding='utf-8')
    except Exception:
        pass
    sys.__excepthook__(kind, error, traceback)
sys.excepthook = failure_artifact
copy = labels()
cases = args.case or ['360,800', '600,900', '840,720', '800,1280', '1280,800', '1600,1000', '900,400']
initial_width, initial_height = map(int, cases[0].split(','))
initial_scale = args.density / 160
adb('shell', 'wm', 'size', f'{round(initial_width*initial_scale)}x{round(initial_height*initial_scale)}')
adb('shell', 'wm', 'density', args.density)
adb('shell', 'cmd', 'locale', 'set-app-locales', package, '--locales', args.locale)
adb('shell', 'settings', 'put', 'system', 'font_scale', args.font_scale)
def launch(destination):
    adb('shell', 'am', 'start', '-S', '-W', '-n', package + '/.debug.ScreenshotActivity',
        '-f', '0x14000000', '--es', 'theme', 'LIQUID_GLASS', '--es', 'destination', destination,
        '--ez', 'dark_theme', 'false' if args.light else 'true',
        '--ez', 'seed_demo', 'true', '--ez', 'github_demo', 'true')

if args.capture_only:
    captures = []
    for case in cases:
        width, height = map(int, case.split(','))
        scale = args.density / 160
        adb('shell', 'wm', 'size', f'{round(width*scale)}x{round(height*scale)}')
        adb('shell', 'wm', 'density', args.density)
        for destination in ['dashboard', 'history', 'connections', 'github', 'settings']:
            launch(destination)
            time.sleep(3)
            name = f'{width}x{height}-{args.locale}-{args.font_scale:g}-{destination}'
            dimensions = capture(name)
            captures.append({'case': case, 'locale': args.locale, 'font_scale': args.font_scale,
                             'destination': destination, 'screen': dimensions, 'file': name + '.png'})
            (args.output / 'captures.json').write_text(json.dumps(captures, ensure_ascii=False, indent=2), encoding='utf-8')
            print('Captured', name, '(visual review required)', flush=True)
    sys.exit(0)

launch('dashboard')
startup_deadline = time.monotonic() + 60
while time.monotonic() < startup_deadline:
    try:
        if find(tree(), copy['navigation_dashboard']):
            break
    except (AssertionError, subprocess.TimeoutExpired):
        pass
    time.sleep(1)
else:
    raise AssertionError('Demo UI did not finish starting')
results = []
for case in cases:
    width, height = map(int, case.split(','))
    scale = args.density / 160
    adb('shell', 'wm', 'size', f'{round(width*scale)}x{round(height*scale)}')
    adb('shell', 'wm', 'density', args.density)
    time.sleep(2)
    prefix = f'{width}x{height}-{args.locale}-{args.font_scale:g}'
    row = {'case': case, 'locale': args.locale, 'font_scale': args.font_scale, 'pages': {}}
    for key in ['navigation_dashboard', 'navigation_trends', 'connections_title', 'github_dashboard_title']:
        label = copy[key]
        tap(label)
        current = tree()
        gears = find(current, copy['settings_title'], description=True)
        assert gears, f'Settings action missing on {label}'
        gear = min(gears, key=lambda n: bounds(n)[1])
        rect = bounds(gear)
        pixel_width, pixel_height = capture(prefix + '-' + key)
        assert any(bounds(n)[1] < 160*scale and bounds(n)[0] < pixel_width
                   for n in find(current, label)), f'{label} header did not become visible'
        row['pages'][key] = {'settings_bounds': rect, 'screen': [pixel_width, pixel_height]}
        if not args.baseline:
            allowance = (80 + max(0, width - 1440)/2)*scale
            assert pixel_width - rect[2] <= allowance, f'Header narrowed on {label}: {rect}, window={pixel_width}'
    tap(copy['settings_title'], description=True)
    current = tree()
    assert find(current, copy['settings_title']), 'Settings pane did not open'
    capture(prefix + '-settings')
    adb('shell', 'input', 'keyevent', 4)
    time.sleep(1)
    assert find(tree(), 'GitHub'), 'Settings did not return to GitHub'
    results.append(row)
    (args.output / 'layout-results.json').write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding='utf-8')
    print('PASS', prefix, flush=True)
print(f'PASS {len(results)} window cases, all tabs and Settings return', flush=True)
