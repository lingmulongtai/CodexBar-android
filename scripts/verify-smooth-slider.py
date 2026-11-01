#!/usr/bin/env python3
"""Verify that a stepped glass slider follows touch smoothly on a disposable emulator."""
import argparse
import struct
import subprocess
import time
import xml.etree.ElementTree as ET
import re

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--adb', default='adb')
args = parser.parse_args()
package = 'com.codexbar.android'

def run(*parts):
    return subprocess.check_output([args.adb, '-s', args.serial, *map(str, parts)], text=True, encoding='utf-8').strip()

def tree():
    for _ in range(5):
        if 'dumped to' in run('shell', 'uiautomator', 'dump', '/sdcard/smooth-slider-test.xml'):
            root = ET.fromstring(run('shell', 'cat', '/sdcard/smooth-slider-test.xml'))
            assert any(n.get('package') == package for n in root.iter('node')), 'App is obscured'
            return root
        time.sleep(0.5)
    raise AssertionError('No fresh UI hierarchy')

def slider_bounds(root):
    node = next(n for n in root.iter('node') if n.get('content-desc') == 'Test slider')
    return list(map(int, re.findall(r'\d+', node.get('bounds'))))

def screenshot():
    raw = subprocess.check_output([args.adb, '-s', args.serial, 'exec-out', 'screencap'])
    width, height, fmt = struct.unpack_from('<III', raw)
    assert fmt == 1, f'Unexpected pixel format {fmt}'
    header = len(raw) - width * height * 4
    assert header in (12, 16), f'Unexpected screencap header length {header}'
    return width, height, memoryview(raw)[header:]

assert run('shell', 'getprop', 'ro.kernel.qemu') == '1', 'Use a disposable emulator'
run('shell', 'am', 'start', '-W', '-f', '0x14000000', '-n', package+'/com.codexbar.android.debug.GlassControlsActivity', '--ez', 'rtl', 'false')
time.sleep(3)
root = tree()
l,t,r,b = slider_bounds(root)
y = (t+b)//2
x0 = (l+r)//2
x1 = l + int((r-l) * .54)
x2 = l + int((r-l) * .60)
try:
    run('shell', 'input', 'touchscreen', 'motionevent', 'DOWN', x0, y)
    time.sleep(.5)
    run('shell', 'input', 'touchscreen', 'motionevent', 'MOVE', x1, y)
    time.sleep(1)
    assert any(n.get('text') == 'Slider value: 50' for n in tree().iter('node'))
    w,h,a = screenshot()
    run('shell', 'input', 'touchscreen', 'motionevent', 'MOVE', x2, y)
    time.sleep(1)
    assert any(n.get('text') == 'Slider value: 50' for n in tree().iter('node'))
    w2,h2,bits = screenshot()
    assert (w,h)==(w2,h2)
    changed = 0
    for row in range(max(0,y-45), min(h,y+45)):
        for col in range(max(0,l), min(w,r)):
            i = (row*w+col)*4
            if sum(abs(a[i+j]-bits[i+j]) for j in range(3)) > 36:
                changed += 1
    assert changed > 250, f'Thumb did not move smoothly within one value step: {changed} pixels'
finally:
    run('shell', 'input', 'touchscreen', 'motionevent', 'CANCEL', x2, y)
print(f'PASS smooth thumb movement within one discrete step ({changed} changed pixels)')
