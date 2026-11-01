#!/usr/bin/env python3
"""Verify real top-level navigation and restored state on a disposable emulator."""
import argparse
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--adb', default='adb')
args = parser.parse_args()
package = 'com.codexbar.android'

def adb(*parts):
    return subprocess.check_output([args.adb, '-s', args.serial, *map(str, parts)], text=True, encoding='utf-8').strip()

def tree():
    for _ in range(5):
        if 'dumped to' in adb('shell', 'uiautomator', 'dump', '/sdcard/codexbar-navigation-test.xml'):
            root = ET.fromstring(adb('shell', 'cat', '/sdcard/codexbar-navigation-test.xml'))
            assert any(n.get('package') == package for n in root.iter('node')), 'App is obscured'
            return root
        time.sleep(0.5)
    raise AssertionError('No fresh UI hierarchy')

def has(root, text): return any(n.get('text') == text for n in root.iter('node'))
def centre(root, label):
    found = [n for n in root.iter('node') if n.get('text') == label]
    assert found, f'Missing {label}'
    # Navigation bar label is below any page title with the same text.
    node = max(found, key=lambda n: int(re.findall(r'\d+', n.get('bounds'))[1]))
    l,t,r,b = map(int, re.findall(r'\d+', node.get('bounds')))
    return (l+r)//2, (t+b)//2

def tap(label):
    adb('shell', 'input', 'tap', *centre(tree(), label))
    time.sleep(1)

assert adb('shell', 'getprop', 'ro.kernel.qemu') == '1', 'Use a disposable emulator'
adb('shell', 'cmd', 'locale', 'set-app-locales', package, '--locales', 'en')
adb('shell', 'am', 'start', '-W', '-f', '0x14000000', '-n', package+'/com.codexbar.android.debug.ScreenshotActivity',
    '--es', 'theme', 'LIQUID_GLASS', '--es', 'destination', 'settings', '--ez', 'dark_theme', 'true')
time.sleep(4)
assert has(tree(), 'Persistent quota status'), 'Settings did not open'
# Scroll away from the first section; navigating back should preserve this position.
adb('shell', 'input', 'swipe', 500, 1700, 500, 650, 350)
time.sleep(1)
assert not has(tree(), 'Persistent quota status'), 'Settings did not scroll'
tap('Usage')
assert has(tree(), 'Running out first'), 'Settings to Usage failed'
tap('Settings')
assert not has(tree(), 'Persistent quota status'), 'Settings scroll position was not restored'
tap('Trends')
assert has(tree(), 'History'), 'Trends did not open'
tap('Usage')
assert has(tree(), 'Running out first'), 'Trends to Usage failed'
tap('Connections')
assert has(tree(), 'Usage source & Windows connection'), 'Connections did not open'
tap('Usage')
assert has(tree(), 'Running out first'), 'Connections to Usage failed'
print('PASS Settings/Trends/Connections to Usage and Settings scroll restoration')
