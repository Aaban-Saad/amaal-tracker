"""Multiple-family checks on a disposable emulator, using local preview only."""
import json
import runpy
import shlex
import time
import xml.etree.ElementTree as ET
from datetime import date, timedelta

h = runpy.run_path('tests/android_smoke.py', run_name='amaal_helpers')
adb, tree, tap_node, shot = [h[k] for k in ['adb', 'tree', 'tap_node', 'shot']]
APP = h['APP']

def match(root, label):
    return next((n for n in root.iter('node') if n.get('text', '').strip() == label or n.get('content-desc', '').strip() == label), None)

def find(label, scroll=False):
    for _ in range(12 if scroll else 5):
        found = match(tree(), label)
        if found is not None:
            return found
        if scroll:
            adb('shell', 'input', 'swipe', '540', '1850', '540', '950', '200')
        else:
            time.sleep(.3)
    raise AssertionError('Missing UI label: ' + label)

def click(label, scroll=False):
    tap_node(find(label, scroll))

def prefs():
    root = ET.fromstring(adb('shell', 'run-as', APP, 'cat', 'shared_prefs/amaal.xml'))
    return {n.get('name'): n.text for n in root}

def entries(family_id):
    return json.loads(prefs().get('entries' if family_id == 'preview' else family_id + '_entries', '[]'))

def select(family):
    find('Select family')
    if prefs().get('activePreviewFamily', 'preview') != family['id']:
        click('Select family')
        click(family['name'])
    assert prefs()['activePreviewFamily'] == family['id']
    find(family['name'])

def fill(value):
    field = next(n for n in tree().iter('node') if n.get('class') == 'android.widget.EditText')
    tap_node(field)
    adb('shell', 'input', 'keyevent', 'KEYCODE_MOVE_END')
    adb('shell', 'input', 'keyevent', *(['KEYCODE_DEL'] * 80))
    adb('shell', 'input', 'text', shlex.quote(value.replace(' ', '%s')))
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')

def assert_summary(points):
    root = tree()
    parents = {child: parent for parent in root.iter() for child in parent}
    current = match(root, 'Race')
    while current is not None:
        if match(current, str(points)) is not None:
            return
        current = parents.get(current)
    raise AssertionError('Expected review points: ' + str(points))

def persistence_check(first, second, original_id):
    # Remember the selected family and its records across process death.
    select(second)
    adb('shell', 'am', 'force-stop', APP)
    adb('shell', 'am', 'start', '-n', APP + '/.MainActivity')
    time.sleep(1)
    click('Explore local preview')
    assert prefs()['activePreviewFamily'] == second['id']
    find('Select family')
    find('Qada', True)
    select(first)
    shot('today-multiple')
    print('PASS: selected family and records persist across restart', flush=True)
    # Leave the preview on the family that was selected before the test.
    original = next(f for f in json.loads(prefs()['previewFamilies']) if f['id'] == original_id)
    select(original)

def main():
    print('Starting multiple-family UI checks', flush=True)
    adb('shell', 'am', 'force-stop', APP)
    adb('shell', 'am', 'start', '-n', APP + '/.MainActivity')
    time.sleep(1)
    click('Explore local preview')
    all_families = json.loads(prefs()['previewFamilies'])
    original_id = prefs()['activePreviewFamily']
    first = next(f for f in all_families if f['id'] == 'preview')
    select(first)
    click('Fajr', True)
    click('Takbir-e-Ula')
    first_logs = entries(first['id'])
    click('Family')
    second = next((f for f in all_families if f['name'] == 'SwitchTest'), None)
    if second is None:
        click('Create another local family', True)
        fill('SwitchTest')
        click('Create family')
        second = next(f for f in json.loads(prefs()['previewFamilies']) if f['name'] == 'SwitchTest')
    else:
        select(second)
    assert second['id'] != first['id']
    find('Select family')
    find('SwitchTest')
    click('Today')
    click('Fajr', True)
    click('Qada')
    assert next(e for e in entries(second['id']) if e['amalId'] == 'fajr')['status'] == 'Qada'
    assert entries(first['id']) == first_logs, 'Writing in family B changed family A records'
    select(first)
    find('Takbir-e-Ula', True)
    select(second)
    find('Qada', True)
    print('PASS: Today selector, independent prayer records and amals', flush=True)
    click('Family')
    select(first)
    find('Rename family')
    find('Everyone belongs here', True)
    shot('family-multiple')
    print('PASS: Family tab selector and active family details', flush=True)
    click('Insights')
    click('Monthly')
    click('Everyone')
    click('Friend (you)')
    select(second)
    find('Everyone')
    # Switching families preserves monthly mode and clears the previous member filter.
    find(date.fromisoformat(adb('shell', 'date', '+%Y-%m-%d').strip()).strftime('%B %Y'))
    current = date.fromisoformat(adb('shell', 'date', '+%Y-%m-%d').strip())
    expected = sum(e['score'] for e in entries(second['id']) if current.replace(day=1).isoformat() <= e['date'] <= current.isoformat())
    assert_summary(expected)
    shot('insights-multiple')
    click('Previous review period')
    previous = current.replace(day=1) - timedelta(days=1)
    select(first)
    find(previous.strftime('%B %Y'))
    click('Next review period')
    expected = sum(e['score'] for e in entries(first['id']) if current.replace(day=1).isoformat() <= e['date'] <= current.isoformat())
    assert_summary(expected)
    find('Leaderboard', True)
    print('PASS: Insights selector, period preservation, member reset, isolated monthly scores', flush=True)
    persistence_check(first, second, original_id)
    print("PASS: multiple-family switching across all three tabs", flush=True)

if __name__ == "__main__":
    main()
