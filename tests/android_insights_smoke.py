"""Run on a disposable emulator with the debug APK installed; creates local preview records."""
import runpy
import time
from pathlib import Path

helpers = runpy.run_path(str(Path(__file__).with_name('android_smoke.py')))
adb, tree, tap, tap_node, shot = [helpers[k] for k in ['adb', 'tree', 'tap', 'tap_node', 'shot']]
APP = helpers['APP']

def locate(label, scroll=True):
    for _ in range(24 if scroll else 1):
        root = tree()
        for node in root.iter('node'):
            if node.get('text') == label or node.get('content-desc') == label:
                return node
        if scroll:
            adb('shell', 'input', 'swipe', '540', '1700', '540', '1200', '500')
            time.sleep(.3)
    raise AssertionError('Missing label: ' + label)

def click(label):
    tap_node(locate(label))

def top():
    for _ in range(4):
        adb('shell', 'input', 'swipe', '540', '700', '540', '1800', '150')
    time.sleep(.5)

def main():
    adb('shell', 'am', 'force-stop', APP)
    adb('shell', 'am', 'start', '-n', APP + '/.MainActivity')
    time.sleep(2)
    if any(n.get('text') == 'Explore local preview' for n in tree().iter('node')):
        tap('Explore local preview')
    tap('Fajr')
    tap('Takbir-e-Ula')
    tap('Insights')
    locate('Race')
    locate('Leaderboard')
    shot('insights-race')
    locate('Friend (you)')
    locate('PREVIOUS PERIOD COMPARISON')
    locate('Points')
    print('PASS: race leaderboard and momentum', flush=True)
    top()
    click('Daily')
    days = [n for n in tree().iter('node') if 'points. Show this day' in n.get('content-desc', '')]
    assert days, 'No accessible day strip'
    tap_node(days[-1])
    locate('YOUR DAY')
    locate('Fajr')
    locate('Takbir-e-Ula')
    locate('+10')
    locate('Not recorded')
    shot('insights-daily')
    print('PASS: daily member checklist', flush=True)
    top()
    click('Salah')
    locate('PRAYER STATUS DISTRIBUTION')
    locate('Takbir-e-Ula')
    shot('insights-salah')
    top()
    click('Habits')
    locate('ACTIVITY CALENDAR')
    # Use the calendar's Salah chip, after the section chip of the same name.
    locate('All deeds')
    tap_node([n for n in tree().iter('node') if n.get('text') == 'Salah'][1])
    shot('insights-habits')
    locate('AMAL BREAKDOWN')
    top()
    click('Monthly')
    locate('ACTIVITY CALENDAR')
    shot('insights-monthly-calendar')
    # A calendar day opens the same member checklist.
    click(next(n.get('content-desc') for n in tree().iter('node') if 'Show details' in n.get('content-desc', '')))
    locate('YOUR DAY')
    top()
    click('Race')
    locate('PREVIOUS PERIOD COMPARISON')
    print('PASS: race, comparison, daily checklist, salah, habits, monthly calendar')

if __name__ == "__main__":
    main()
