"""Run against a disposable emulator with the debug APK already installed."""
import os
import json
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ADB = os.environ.get('ADB', 'adb')
APP = 'com.amaal.tracker'
OUT = Path(__file__).resolve().parents[1] / 'docs' / 'screenshots'
OUT.mkdir(parents=True, exist_ok=True)

def adb(*args):
    return subprocess.check_output([ADB, *args], text=True, stderr=subprocess.DEVNULL)

def tree():
    adb('shell', 'uiautomator', 'dump', '/sdcard/amaal-ui.xml')
    return ET.fromstring(adb('shell', 'cat', '/sdcard/amaal-ui.xml'))

def find(label):
    for _ in range(5):
        root = tree()
        for n in root.iter('node'):
            if n.get('text') == label or n.get('content-desc') == label:
                return n
        time.sleep(.5)
    raise AssertionError(f'UI label not found: {label}')

def tap(label):
    n = find(label)
    tap_node(n)

def tap_node(n):
    numbers = n.get('bounds').replace('][', ',').strip('[]').split(',')
    x1, y1, x2, y2 = map(int, numbers)
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
    time.sleep(.5)

def shot(name):
    adb('shell', 'screencap', '-p', '/sdcard/amaal-screen.png')
    adb('pull', '/sdcard/amaal-screen.png', str(OUT / f'{name}.png'))

def main():
    adb('shell', 'am', 'start', '-n', APP + '/.MainActivity')
    time.sleep(1)
    if any(n.get('text') == 'Explore local preview' for n in tree().iter('node')):
        shot('welcome')
        tap('Explore local preview')
    tap('Fajr')
    tap('Takbir-e-Ula')
    find('Takbir-e-Ula')
    shot('today')
    tap('Family')
    find('Everyone belongs here')
    shot('family')
    tap('Insights')
    find('Race')
    prefs = ET.fromstring(adb('shell', 'run-as', APP, 'cat', 'shared_prefs/amaal.xml'))
    stored_values = {n.get('name'): n.text for n in prefs}
    active = stored_values.get('activePreviewFamily', 'preview')
    stored = stored_values.get('entries' if active == 'preview' else active + '_entries', '[]')
    today = adb('shell', 'date', '+%Y-%m-%d').strip()
    expected = sum(e['score'] for e in json.loads(stored) if e['date'] == today)
    assert any(n.get('text') == str(expected) for n in tree().iter('node')), 'Expected points from saved preview records'
    shot('insights')
    adb('shell', 'am', 'force-stop', APP)
    adb('shell', 'am', 'start', '-n', APP + '/.MainActivity')
    time.sleep(1)
    tap('Explore local preview')
    find('Takbir-e-Ula')
    tap('Settings')
    find('Local reminders')
    shot('settings')
    tap('Cancel')
    tap('Settings')
    root = tree()
    notification_switch = next(n for n in root.iter('node') if n.get('checkable') == 'true')
    if notification_switch.get('checked') != 'true':
        tap_node(notification_switch)
    tap('Save preferences')
    root = tree()
    if any(n.get('text') == 'Allow' for n in root.iter('node')):
        tap('Allow')
    time.sleep(1)
    alarms = adb('shell', 'dumpsys', 'alarm')
    assert APP + '/.ReminderReceiver' in alarms or 'com.amaal.tracker.ReminderReceiver' in alarms, 'Expected scheduled local alarms'
    print('PASS: local alarms scheduled; checking reboot restoration', flush=True)
    # Model a user-launched app, rather than the emulator's sideloaded 'never used' bucket.
    adb('shell', 'am', 'set-standby-bucket', APP, 'active')
    adb('reboot')
    for _ in range(60):
        time.sleep(2)
        try:
            if adb('shell', 'getprop', 'sys.boot_completed').strip() == '1':
                break
        except subprocess.CalledProcessError:
            pass
    else:
        raise AssertionError('Emulator failed to reboot')
    # BOOT_COMPLETED delivery can lag behind sys.boot_completed and user unlock.
    for _ in range(45):
        alarms = adb('shell', 'dumpsys', 'alarm')
        if APP + '/.ReminderReceiver' in alarms or 'com.amaal.tracker.ReminderReceiver' in alarms:
            break
        time.sleep(2)
    else:
        raise AssertionError('Expected alarms restored after reboot')
    adb('shell', 'am', 'start', '-n', APP + '/.MainActivity')
    time.sleep(1)
    tap('Explore local preview')
    tap('Settings')
    notification_switch = next(n for n in tree().iter('node') if n.get('checkable') == 'true')
    if notification_switch.get('checked') == 'true':
        tap_node(notification_switch)
    tap('Save preferences')
    print('PASS: launch, local preview, weighted prayer tracking, family, analytics, settings, process-restart persistence, local alarm scheduling, and reboot restoration')

if __name__ == "__main__":
    main()
