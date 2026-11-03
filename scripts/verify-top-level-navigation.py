#!/usr/bin/env python3
"""Exercise connected paging and Settings return on a disposable English demo emulator."""
import argparse
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--adb', default='adb')
parser.add_argument('--adb-port', default='5037')
parser.add_argument('--screenshots', type=Path)
args = parser.parse_args()
package = 'com.codexbar.android'
base = [args.adb, '-P', args.adb_port, '-s', args.serial]

def adb(*parts):
    return subprocess.check_output(base + list(map(str, parts)), text=True, encoding='utf-8').strip()

def tree():
    for _ in range(5):
        result = adb('shell', 'uiautomator', 'dump', '/sdcard/codexbar-navigation-test.xml')
        if 'dumped to' in result:
            root = ET.fromstring(adb('shell', 'cat', '/sdcard/codexbar-navigation-test.xml'))
            assert any(n.get('package') == package for n in root.iter('node')), 'App is obscured or exited'
            return root
        time.sleep(.5)
    raise AssertionError('No fresh UI hierarchy')

def has(root, label):
    return any(n.get('text') == label for n in root.iter('node'))

def centre(label, description=False):
    attr = 'content-desc' if description else 'text'
    nodes = [n for n in tree().iter('node') if n.get(attr) == label]
    assert nodes, f'Missing {label}'
    # Bottom navigation is below any page title with the same text.
    n = max(nodes, key=lambda n: int(re.findall(r'\d+', n.get('bounds'))[1]))
    l,t,r,b = map(int, re.findall(r'\d+', n.get('bounds')))
    return (l+r)//2, (t+b)//2

def tap(label, description=False, settle=True):
    adb('shell', 'input', 'tap', *centre(label, description))
    if settle: time.sleep(1)

def capture(name):
    if args.screenshots:
        args.screenshots.mkdir(parents=True, exist_ok=True)
        (args.screenshots / (name + '.png')).write_bytes(subprocess.check_output(base + ['exec-out','screencap','-p']))

assert adb('shell', 'getprop', 'ro.kernel.qemu') == '1', 'Use only a disposable emulator'
adb('shell', 'cmd', 'locale', 'set-app-locales', package, '--locales', 'en')
adb('shell', 'am', 'start', '-S', '-W', '-n', package+'/.debug.ScreenshotActivity',
    '--es', 'theme', 'LIQUID_GLASS', '--es', 'destination', 'dashboard',
    '--ez', 'dark_theme', 'true', '--ez', 'seed_demo', 'true', '--ez', 'github_demo', 'true')
for _ in range(20):
    time.sleep(1)
    if has(tree(), 'Running out first'): break
else: raise AssertionError('Demo did not start')

# The original regression: Settings must not replace any origin tab's page.
for origin, marker in [('Trends', 'History'), ('Connections', 'Usage source & Windows connection'), ('GitHub', 'Ryuta · Demo')]:
    tap(origin)
    assert has(tree(), marker), f'{origin} did not open'
    for method in ['same-tab', 'back', 'early-back']:
        tap('Settings', description=True, settle=method != 'early-back')
        if method != 'early-back':
            assert has(tree(), 'Persistent quota status'), 'Settings did not open'
        if method == 'same-tab': tap(origin)
        else:
            adb('shell', 'input', 'keyevent', 4)
            time.sleep(1)
        assert has(tree(), marker), f'{origin} was replaced by Settings ({method})'
        assert not has(tree(), 'Persistent quota status'), 'Settings remains on top'
        capture(origin.lower()+'-return-'+method)
    print('PASS', origin, 'Settings return by selected tab, Back and early Back')

tap('Usage')
assert has(tree(), 'Running out first')
width,height = map(int, re.findall(r'\d+', adb('shell','wm','size').splitlines()[-1])[-2:])
x,y = int(width*.84), int(height*.55)
adb('shell','input','touchscreen','motionevent','DOWN',x,y)
for ratio in [.80,.72,.64,.56,.49]:
    adb('shell','input','touchscreen','motionevent','MOVE',int(width*ratio),y)
    time.sleep(.05)
capture('held-adjacent-pages')
# Reverse before releasing: should return to Usage without a route change.
adb('shell','input','touchscreen','motionevent','MOVE',x-8,y)
adb('shell','input','touchscreen','motionevent','UP',x-8,y)
time.sleep(1)
assert has(tree(), 'Running out first'), 'Reverse/cancel lost Usage'
capture('reversed-to-usage')
print('PASS continuous held drag, reverse and cancellation')
