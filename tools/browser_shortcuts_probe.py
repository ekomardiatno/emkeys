#!/usr/bin/env python3
"""Probe F6/Ctrl+PgUp/PgDn using two disposable Chrome oracle tabs.

Requires emkeys enabled and a fresh KeyboardDeviceTest#exportKeyBounds trace in
the same orientation. Reports delivery/interception, not a pass/fail expectation
for browser shortcuts. No editor or shell input is used. Leaves two oracle tabs
open, stops their loopback collector, restores the original IME and Android task.
Output screenshots may contain browser UI; keep results outside the repository.
"""

import argparse
import json
from pathlib import Path
import re
import shlex
import struct
import subprocess
import threading
import time
import xml.etree.ElementTree as ET

from keyoracle_server import OracleServer


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--serial', required=True)
    parser.add_argument('--bounds', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    adb = [args.adb, '-s', args.serial]

    def run(*command):
        return subprocess.check_output(adb + list(command), text=True, timeout=20)

    line = next(line for line in args.bounds.read_text().splitlines() if 'keys_json=' in line)
    caps = json.loads(line.split('keys_json=', 1)[1])
    assert len(caps) == 84, 'Need complete key bounds'
    points = {cap['label']: ((cap['left'] + cap['right']) // 2,
                             (cap['top'] + cap['bottom']) // 2) for cap in caps}
    activities = run('shell', 'dumpsys', 'activity', 'activities')
    task = re.search(r'topResumedActivity=.*?\bt(\d+)\b', activities)
    assert task, 'Cannot identify starting Android task'
    original_ime = run('shell', 'settings', 'get', 'secure', 'default_input_method').strip()
    emkeys = 'com.ekomardiatno.emkeys/.ime.EmkeysInputMethodService'
    server = OracleServer(0)
    worker = threading.Thread(target=server.serve_forever, daemon=True)
    worker.start()
    port = str(server.server_port)
    mapped = False
    results = []
    pad_point = None

    def dump():
        run('shell', 'uiautomator', 'dump', '/data/local/tmp/emkeys-shortcut-probe.xml')
        return ET.fromstring(run('exec-out', 'cat', '/data/local/tmp/emkeys-shortcut-probe.xml'))

    def fixture(root):
        url = next((node.get('text', '') for node in root.iter('node')
                    if node.get('resource-id') == 'com.android.chrome:id/url_bar'), '')
        if f'localhost:{port}/keyoracle.html' not in url:
            return None
        return next((name for name in ('a', 'b') if f'fixture={name}' in url), None)

    def page(require_pad=True):
        deadline = time.monotonic() + 20
        while True:
            root = dump()
            if fixture(root) and (not require_pad or any(node.get('resource-id') == 'pad' for node in root.iter('node'))):
                return root
            if time.monotonic() >= deadline:
                raise AssertionError('Stopped: owned oracle page is not visible')

    def guard():
        # Check immediately before each touch, without injecting into another app.
        script = ("dumpsys activity activities | grep topResumedActivity | "
                  "grep -q 'com.android.chrome/com.google.android.apps.chrome.Main' && "
                  f'[ "$(settings get secure default_input_method)" = {shlex.quote(emkeys)} ]')
        return script

    def tap(x, y):
        run('shell', guard() + f' && input tap {x} {y}')

    def focus_pad(root):
        nonlocal pad_point
        assert fixture(root), 'Refusing to focus a non-fixture page'
        pad = next((node for node in root.iter('node') if node.get('resource-id') == 'pad'), None)
        if pad is not None:
            x1, y1, x2, y2 = map(int, re.findall(r'\d+', pad.get('bounds')))
            if x2 > x1 and y2 > y1:
                pad_point = ((x1 + x2) // 2, (y1 + y2) // 2)
        # Chrome can temporarily omit its WebView accessibility subtree after a tab
        # switch. Both owned pages have identical, fixed markup in the same viewport.
        assert pad_point is not None, 'No known fixture pad bounds'
        image = subprocess.check_output(adb + ['exec-out', 'screencap', '-p'], timeout=20)
        width, height = struct.unpack('>II', image[16:24])
        assert abs(max(cap['right'] for cap in caps) - width) < 20, 'Wrong bounds orientation'
        assert all(0 <= x < width and 0 <= y < height for x, y in points.values()), 'Off-screen bounds'
        tap(*pad_point)
        deadline = time.monotonic() + 6
        while 'mInputShown=true' not in run('shell', 'dumpsys', 'input_method'):
            assert time.monotonic() < deadline, 'Fixture tap did not show the IME'
            time.sleep(0.1)
        dump()  # Wait for IME entry/inset animations to settle.

    try:
        run('shell', 'ime', 'set', emkeys)
        run('reverse', '--no-rebind', 'tcp:' + port, 'tcp:' + port)
        mapped = True
        for name in ('a', 'b'):
            url = f'http://localhost:{port}/keyoracle.html?report=1&block=1&fixture={name}'
            run('shell', 'am', 'start', '-n', 'com.android.chrome/com.google.android.apps.chrome.Main',
                '-a', 'android.intent.action.VIEW', '-d', shlex.quote(url), '--ez', 'create_new_tab', 'true')
            root = page()
            assert fixture(root) == name, 'Wrong fixture loaded'
            focus_pad(root)

        for name, labels, expected_code, ctrl in [
            ('F6', ['F6'], 'F6', False),
            ('Ctrl-PgUp', ['Ctrl', 'PgUp'], 'PageUp', True),
            ('Ctrl-PgDn', ['Ctrl', 'PgDn'], 'PageDown', True),
        ]:
            root = page(require_pad=False)
            before = fixture(root)
            def url_has_focus():
                return any(node.get('resource-id') == 'com.android.chrome:id/url_bar' and
                           node.get('focused') == 'true' for node in root.iter('node'))
            for _ in range(3):
                if not url_has_focus():
                    break
                # Reset native omnibox focus, not an emkeys dispatch measurement.
                # Escape cannot navigate web history the way Android Back can.
                run('shell', guard() + ' && input keyevent KEYCODE_ESCAPE')
                root = page(require_pad=False)
            assert not url_has_focus(), 'Could not dismiss native omnibox focus'
            focus_pad(root)
            with server.lock:
                server.events.clear()
            for label in labels:
                tap(*points[label])
            # Observe asynchronous reports, then inspect the native browser focus.
            time.sleep(0.3)
            root = page(require_pad=False)
            after = fixture(root)
            with server.lock:
                events = sorted(server.events, key=lambda event: event['n'])
            keyboard = [event for event in events if event['type'] in ('keydown', 'keyup')]
            delivered = any(event['type'] == 'keydown' and event['code'] == expected_code and
                            event['ctrl'] == ctrl and event['fixture'] == before for event in keyboard)
            assert all(event['trusted'] and not event['composing'] and event['keyCode'] != 229
                       for event in keyboard), 'Invalid renderer event'
            url_focused = any(node.get('resource-id') == 'com.android.chrome:id/url_bar' and
                              node.get('focused') == 'true' for node in root.iter('node'))
            observed = ('delivered-to-page' if delivered else 'browser-tab-switch' if before != after
                        else 'browser-url-focus' if url_focused else 'not-delivered')
            result = dict(shortcut=name, observed=observed, before=before, after=after,
                          url_focused=url_focused, events=events)
            results.append(result)
            image = subprocess.check_output(adb + ['exec-out', 'screencap', '-p'], timeout=20)
            args.output.with_name(args.output.stem + '-' + name + '.png').write_bytes(image)
            print(f'{name}: {observed} (fixture {before} -> {after})', flush=True)
    except Exception:
        args.output.with_name(args.output.stem + '-failure.png').write_bytes(
            subprocess.check_output(adb + ['exec-out', 'screencap', '-p'], timeout=20))
        raise
    finally:
        try:
            args.output.write_text(json.dumps(results, indent=2))
        finally:
            try:
                if mapped:
                    run('reverse', '--remove', 'tcp:' + port)
            finally:
                try:
                    run('shell', 'ime', 'set', original_ime)
                    run('shell', 'am', 'task', 'focus', task.group(1))
                finally:
                    server.shutdown()
                    server.server_close()
                    worker.join()


if __name__ == '__main__':
    main()
