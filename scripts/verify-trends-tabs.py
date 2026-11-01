#!/usr/bin/env python3
"""Verify held History/Spend selection on a disposable emulator with synthetic data."""
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
        if 'dumped to' in adb('shell', 'uiautomator', 'dump', '/sdcard/trends-tabs-test.xml'):
            root = ET.fromstring(adb('shell', 'cat', '/sdcard/trends-tabs-test.xml'))
            assert any(n.get('package') == package for n in root.iter('node')), 'App is not visible'
            return root
        time.sleep(0.5)
    raise AssertionError('No fresh UI hierarchy')

def tab(root, label):
    for n in root.iter('node'):
        if n.get('focusable') == 'true' and n.get('selected') in ('true', 'false') and any(c.get('text') == label for c in n.iter('node')):
            bounds = list(map(int, re.findall(r'\d+', n.get('bounds', ''))))
            if len(bounds) == 4:
                l, t, r, b = bounds
                return n.get('selected') == 'true', ((l+r)//2, (t+b)//2)
    raise AssertionError('Missing tab: ' + label)

def drag(start, end):
    adb('shell', 'input', 'touchscreen', 'draganddrop', *start, *end, 600)
    time.sleep(1)

assert adb('shell', 'getprop', 'ro.kernel.qemu') == '1', 'Use a disposable emulator'
adb('shell', 'cmd', 'locale', 'set-app-locales', package, '--locales', 'en')
adb('shell', 'am', 'start', '-W', '-f', '0x14000000', '-n', package+'/.debug.ScreenshotActivity',
    '--es', 'theme', 'LIQUID_GLASS', '--es', 'destination', 'history')
time.sleep(3)
root = tree()
history, h = tab(root, 'History')
spend, s = tab(root, 'Spend')
assert history and not spend

drag(h, s)
root = tree()
assert tab(root, 'Spend')[0] and not tab(root, 'History')[0], 'History to Spend drag failed'
h = tab(root, 'History')[1]
s = tab(root, 'Spend')[1]
drag(s, h)
root = tree()
assert tab(root, 'History')[0] and not tab(root, 'Spend')[0], 'Spend to History drag failed'

h = tab(root, 'History')[1]
s = tab(root, 'Spend')[1]
adb('shell', 'input', 'touchscreen', 'motionevent', 'DOWN', *h)
time.sleep(0.8)
adb('shell', 'input', 'touchscreen', 'motionevent', 'MOVE', *s)
time.sleep(0.3)
adb('shell', 'input', 'touchscreen', 'motionevent', 'CANCEL', *s)
root = tree()
assert tab(root, 'History')[0], 'Cancelled drag changed the page'
adb('shell', 'input', 'tap', *tab(root, 'Spend')[1])
time.sleep(1)
assert tab(tree(), 'Spend')[0], 'Tap navigation failed'
print('PASS History/Spend held drag in both directions, cancellation, and tap')
