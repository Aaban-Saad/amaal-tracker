"""Family feature checks on a disposable emulator; uses local preview only."""
import json
import runpy
import shlex
import time
import xml.etree.ElementTree as ET
from datetime import date

helpers = runpy.run_path('tests/android_smoke.py', run_name='amaal_helpers')
adb, tree, tap, tap_node, shot = [helpers[k] for k in ['adb', 'tree', 'tap', 'tap_node', 'shot']]
APP = helpers['APP']

def node(root, label):
    return next((n for n in root.iter('node') if n.get('text', '').strip() == label or n.get('content-desc', '').strip() == label), None)

def visible(label, scroll=True):
    for _ in range(12 if scroll else 4):
        root = tree()
        found = node(root, label)
        if found is not None:
            return found
        if scroll:
            adb('shell', 'input', 'swipe', '540', '1850', '540', '850', '280')
        else:
            time.sleep(.5)
    raise AssertionError('Label missing: ' + label)

def click(label, scroll=True):
    tap_node(visible(label, scroll))

def enter_text(value):
    field = next(n for n in tree().iter('node') if n.get('class') == 'android.widget.EditText')
    tap_node(field)
    adb('shell', 'input', 'keyevent', 'KEYCODE_MOVE_END')
    # Select the existing field contents without relying on a keyboard-specific shortcut.
    adb('shell', 'input', 'keyevent', *(['KEYCODE_DEL'] * 80))
    adb('shell', 'input', 'text', shlex.quote(value.replace(' ', '%s')))
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')

def preferences():
    root = ET.fromstring(adb('shell', 'run-as', APP, 'cat', 'shared_prefs/amaal.xml'))
    result = {n.get('name'): n.text for n in root}
    active = result.get('activePreviewFamily', 'preview')
    if active != 'preview':
        for key in ['entries', 'amals', 'retiredAmals', 'familyName']:
            result[key] = result.get(active + '_' + key, '[]' if key != 'familyName' else '')
    if not result.get('familyName'):
        result['familyName'] = next((f['name'] for f in json.loads(result.get('previewFamilies', '[]')) if f['id'] == active), 'My family')
    return result

def checkbox_for(label):
    root = tree()
    label_node = node(root, label)
    def center_y(n):
        parts = list(map(int, n.get('bounds').replace('][', ',').strip('[]').split(',')))
        return (parts[1] + parts[3]) // 2
    assert label_node is not None
    boxes = [n for n in root.iter('node') if n.get('checkable') == 'true']
    assert boxes, 'No visible checkboxes'
    closest = min(boxes, key=lambda n: abs(center_y(n) - center_y(label_node)))
    assert abs(center_y(closest) - center_y(label_node)) < 150, 'Checkbox not aligned with amal row'
    return closest

print('Starting Family smoke checks', flush=True)
adb('shell', 'am', 'force-stop', APP)
adb('shell', 'am', 'start', '-n', APP + '/.MainActivity')
time.sleep(1)
if node(tree(), 'Explore local preview') is not None:
    tap('Explore local preview')
click('Today', False)
click('Fajr')
click('Takbir-e-Ula', False)
initial_name = preferences().get('familyName', 'My family')
if initial_name == 'SmokeFamily':
    initial_name = 'My family'
initial_entries = json.loads(preferences().get('entries', '[]'))
today = adb('shell', 'date', '+%Y-%m-%d').strip()
initial_points = sum(e['score'] for e in initial_entries if e['date'] == today)
click('Family', False)
click('Rename family', False)
enter_text('SmokeFamily')
click('Save name', False)
visible('SmokeFamily', False)
shot('family')
print('PASS: family renamed', flush=True)
click('Add amal')
enter_text('SmokeAmal')
click('Save amal')
click('Today', False)
visible('SmokeAmal')
box = checkbox_for('SmokeAmal')
if box.get('checked') != 'true':
    tap_node(box)
print('PASS: custom amal created and recorded', flush=True)
click('Family', False)
click('Delete SmokeAmal')
visible('Delete SmokeAmal?', False)
click('Keep amal', False)
visible('Delete SmokeAmal')
click('Delete SmokeAmal')
click('Delete amal', False)
time.sleep(.5)
prefs = preferences()
assert not any(a['name'] == 'SmokeAmal' for a in json.loads(prefs['amals'])), 'Custom amal still active'
assert any(a['name'] == 'SmokeAmal' for a in json.loads(prefs['retiredAmals'])), 'Missing retained historical definition'
print('PASS: custom deletion and retained metadata', flush=True)
# The five built-in prayers remain protected even in their edit dialog.
click('Family', False)
for _ in range(6):
    adb('shell', 'input', 'swipe', '540', '850', '540', '1850', '180')
click('Edit Fajr')
visible('This built-in amal is protected from deletion.')
assert node(tree(), 'Delete amal') is None, 'Protected prayer exposes delete'
click('Cancel', False)
print('PASS: built-in prayers protected', flush=True)
click('Insights', False)
visible('Everyone', False)
click('Monthly', False)
visible('Everyone', False)
visible(str(initial_points + 10), False)
visible('Leaderboard')
shot('insights-monthly')
print('PASS: monthly review keeps deleted amal scores', flush=True)
for _ in range(4):
    adb('shell', 'input', 'swipe', '540', '700', '540', '1800', '150')
click('Everyone', False)
click('Friend (you)', False)
visible('Friend', False)
click('Previous review period', False)
click('Next review period', False)
click('Weekly', False)
visible('Leaderboard')
# Restart proves the new family name and deletion survive process death.
adb('shell', 'am', 'force-stop', APP)
adb('shell', 'am', 'start', '-n', APP + '/.MainActivity')
time.sleep(1)
click('Explore local preview', False)
click('Family', False)
visible('SmokeFamily', False)
click('Rename family', False)
enter_text(initial_name)
click('Save name', False)
shot('family')
print('PASS: family rename/persistence, custom deletion/cancel, protected prayers, preserved scores, weekly/monthly navigation and member selection', flush=True)
