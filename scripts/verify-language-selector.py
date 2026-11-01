#!/usr/bin/env python3
"""Verify the glass language selector on a disposable emulator."""
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

def adb(*args_):
    return subprocess.check_output([args.adb, '-s', args.serial, *map(str, args_)], text=True, encoding='utf-8').strip()

def tree():
    for _ in range(5):
        if 'dumped to' in adb('shell', 'uiautomator', 'dump', '/sdcard/language-selector-test.xml'):
            return ET.fromstring(adb('shell', 'cat', '/sdcard/language-selector-test.xml'))
        time.sleep(.5)
    raise AssertionError('No UI hierarchy')

def find_tab(root, label):
    for n in root.iter('node'):
        if n.get('selected') in ('true', 'false') and n.get('focusable') == 'true' and any(c.get('text') == label for c in n.iter('node')):
            l,t,r,b = map(int, re.findall(r'\d+', n.get('bounds')))
            return n, ((l+r)//2, (t+b)//2)
    return None

def show_language():
    for _ in range(8):
        root = tree()
        if find_tab(root, '日本語') and find_tab(root, 'English'):
            return root
        adb('shell', 'input', 'swipe', 500, 1850, 500, 700, 400)
    raise AssertionError('Language selector is not visible')

def wait_locale(tag):
    for _ in range(20):
        current = adb('shell', 'cmd', 'locale', 'get-app-locales', package)
        if '[' + tag + ']' in current:
            return
        time.sleep(.25)
    raise AssertionError('Locale did not change to ' + tag)

assert adb('shell', 'getprop', 'ro.kernel.qemu') == '1', 'Use a disposable emulator'
original_scale = adb('shell', 'settings', 'get', 'global', 'animator_duration_scale')
try:
    adb('shell', 'cmd', 'locale', 'set-app-locales', package, '--locales', 'en')
    adb('shell', 'am', 'force-stop', package)
    adb('shell', 'am', 'start', '-W', '-n', package+'/.debug.ScreenshotActivity', '--es', 'theme', 'LIQUID_GLASS', '--es', 'destination', 'settings')
    root = show_language()
    assert find_tab(root, 'English')[0].get('selected') == 'true'
    adb('shell', 'input', 'tap', *find_tab(root, '日本語')[1])
    wait_locale('ja')
    root = show_language()
    assert find_tab(root, '日本語')[0].get('selected') == 'true'
    # Reduced-motion users must still be able to change language immediately.
    adb('shell', 'settings', 'put', 'global', 'animator_duration_scale', '0')
    adb('shell', 'input', 'tap', *find_tab(root, 'English')[1])
    wait_locale('en')
    root = show_language()
    assert find_tab(root, 'English')[0].get('selected') == 'true'
    print('PASS language selection, locale recreation, and reduced-motion selection')
finally:
    if original_scale == 'null':
        adb('shell', 'settings', 'delete', 'global', 'animator_duration_scale')
    else:
        adb('shell', 'settings', 'put', 'global', 'animator_duration_scale', original_scale)
