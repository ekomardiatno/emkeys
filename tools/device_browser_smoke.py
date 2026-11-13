#!/usr/bin/env python3
"""Verify real emkeys touches in an isolated Chrome oracle, without touching project files.

Requires an idle tablet, emkeys enabled, and output from KeyboardDeviceTest's
exportKeyBounds method in the SAME orientation. Example:
  python3 tools/device_browser_smoke.py --serial DEVICE --bounds instrumentation.txt \
      --output /path/to/browser-results.json
Only standard Python modules are used. The collector is loopback-only and its adb
reverse mapping is removed on exit and the original IME is restored. The test leaves
the oracle tab open (its local server is stopped).
"""

import argparse
import json
from pathlib import Path
import re
import shlex
import subprocess
import struct
import threading
import time
import xml.etree.ElementTree as ET

from keyoracle_server import OracleServer


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--bounds", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    adb = [args.adb, "-s", args.serial]

    def run(*command):
        return subprocess.check_output(adb + list(command), text=True, timeout=20)

    line = next(line for line in args.bounds.read_text().splitlines() if "keys_json=" in line)
    caps = json.loads(line.split("keys_json=", 1)[1])
    assert len(caps) == 84, "Need a complete 84-key bounds export"
    points = {cap["label"]: ((cap["left"] + cap["right"]) // 2,
                              (cap["top"] + cap["bottom"]) // 2) for cap in caps}

    def dump():
        run("shell", "uiautomator", "dump", "/data/local/tmp/emkeys-oracle-ui.xml")
        return ET.fromstring(run("exec-out", "cat", "/data/local/tmp/emkeys-oracle-ui.xml"))

    def guard():
        active = run("shell", "dumpsys", "activity", "activities")
        assert any("topResumedActivity" in line and
                   "com.android.chrome/com.google.android.apps.chrome.Main" in line
                   for line in active.splitlines()), "Stopped: Chrome test activity is not foreground"
        assert run("shell", "settings", "get", "secure", "default_input_method").strip() == \
            "com.ekomardiatno.emkeys/.ime.EmkeysInputMethodService", "Stopped: emkeys is no longer selected"

    def tap(label):
        guard()
        x, y = points[label]
        run("shell", "input", "tap", str(x), str(y))

    server = OracleServer(0)
    worker = threading.Thread(target=server.serve_forever, daemon=True)
    worker.start()
    port = str(server.server_port)
    mapped = False
    results = []
    original_ime = run("shell", "settings", "get", "secure", "default_input_method").strip()
    try:
        run("shell", "ime", "set", "com.ekomardiatno.emkeys/.ime.EmkeysInputMethodService")
        run("reverse", "--no-rebind", "tcp:" + port, "tcp:" + port)
        mapped = True
        url = f"http://localhost:{port}/keyoracle.html?report=1&block=1"
        run("shell", "am", "start", "-n", "com.android.chrome/com.google.android.apps.chrome.Main",
            "-a", "android.intent.action.VIEW", "-d", shlex.quote(url))
        # Chrome can expose an empty WebView while a new localhost origin loads.
        # Wait for the fixture itself, not just for the activity to become idle.
        deadline = time.monotonic() + 20
        root = dump()
        def loaded():
            return any("emkeys key oracle" in (node.get("text", "") + node.get("content-desc", ""))
                       for node in root.iter("node"))
        while not loaded() and time.monotonic() < deadline:
            root = dump()
        assert loaded(), "Oracle page did not load"
        pad = next(node for node in root.iter("node") if node.get("class") == "android.widget.EditText"
                   and "url_bar" not in node.get("resource-id", ""))
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", pad.get("bounds")))
        guard()
        run("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
        # A hierarchy dump waits for input-window animations to settle.
        dump()
        screenshot = subprocess.check_output(adb + ["exec-out", "screencap", "-p"], timeout=20)
        width, height = struct.unpack(">II", screenshot[16:24])
        assert abs(max(cap["right"] for cap in caps) - width) < 20, "Bounds orientation does not match the screen"
        assert all(0 <= x < width and 0 <= y < height for x, y in points.values()), "Off-screen key bounds"

        def trace():
            with server.lock:
                return sorted(server.events, key=lambda event: event["n"])

        def case(name, action, expected, check=None):
            guard()
            with server.lock:
                server.events.clear()
            action()
            deadline = time.monotonic() + 3
            events = trace()
            modifiers = {"ControlLeft", "ShiftLeft", "AltLeft"}
            def base_down(events):
                return [e for e in events if e["type"] == "keydown" and e.get("code") not in modifiers]
            while len(base_down(events)) < len(expected) and time.monotonic() < deadline:
                time.sleep(0.05)
                events = trace()
            time.sleep(0.1)  # Include the final asynchronous keyup report.
            events = trace()
            downs = base_down(events)
            actual = [e["key"] for e in downs]
            result = {"case": name, "expected": expected, "actual": actual, "events": events}
            results.append(result)
            assert actual == expected, f"{name}: expected {expected}, got {actual}"
            keyboard = [e for e in events if e["type"] in ("keydown", "keyup")]
            assert all(e.get("code") and e["keyCode"] != 229 and e["trusted"] and
                       not e["composing"] for e in keyboard), f"{name}: invalid/untrusted event"
            if check:
                check(events, downs)
            result["passed"] = True
            print(f"PASS {name}: {len(downs)} base presses, trusted events with scancodes", flush=True)

        def taps(labels):
            for label in labels:
                tap(label)

        def modified_taps(modifier, labels):
            for label in labels:
                taps([modifier, label])

        alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        case("alphabet", lambda: taps(alphabet), list(alphabet.lower()))
        symbols = list("1234567890`-=[]\\;',./")
        case("digits and punctuation", lambda: taps(symbols), symbols)
        shifted = list("!@#$%^&*()~_+{}|:\"<>?")
        case("shifted symbols", lambda: modified_taps("Shift", symbols), shifted)

        def chord_check(events, downs):
            assert [e["code"] for e in events] == ["ControlLeft", "ShiftLeft", "KeyP", "KeyP", "ShiftLeft", "ControlLeft"]
            assert downs[0]["ctrl"] and downs[0]["shift"]
            assert not events[-1]["ctrl"] and not events[-1]["shift"]
        case("Ctrl+Shift+P envelope", lambda: taps(["Ctrl", "Shift", "P"]), ["P"], chord_check)
        case("Ctrl+W reaches page", lambda: taps(["Ctrl", "W"]), ["w"],
             lambda events, downs: assert_true(downs[0]["ctrl"]))
        case("Fn navigation and B/P/S/K", lambda: modified_taps("Fn", ["←", "→", "B", "P", "S", "K"]),
             ["Home", "End", "Pause", "Pause", "PrintScreen", "ScrollLock"])
        functions = [f"F{i}" for i in range(1, 13) if i != 6]
        case("function row except F6 (separate browser probe)", lambda: taps(functions), functions)
        case("Caps with Shift inversion", lambda: taps(["Caps Lock", "A", "1", "Shift", "A", "Caps Lock"]), ["A", "1", "a"])
        case("FnLock and bare Escape", lambda: taps(["Fn", "Esc", "←", "Esc", "Fn", "Esc"]), ["Home", "Escape"])

        # A real held touch, not adb keyevent repeat.
        guard()
        with server.lock:
            server.events.clear()
        tap("Ctrl")
        x, y = points["←"]
        run("shell", "input", "swipe", str(x), str(y), str(x), str(y), "1100")
        time.sleep(0.2)
        events = trace()
        arrows = [e for e in events if e.get("code") == "ArrowLeft" and e["type"] == "keydown"]
        assert len(arrows) >= 5 and all(e["ctrl"] and not e["repeat"] and e["trusted"] for e in arrows)
        ctrl = [e for e in events if e.get("code") == "ControlLeft"]
        assert [e["type"] for e in ctrl] == ["keydown", "keyup"]
        results.append({"case": "held Ctrl+Left repeat", "passed": True, "presses": len(arrows), "events": events})
        print(f"PASS held Ctrl+Left repeat: {len(arrows)} pairs inside one Ctrl envelope", flush=True)
    finally:
        # Always attempt every cleanup step, even if writing the result or adb fails.
        try:
            args.output.write_text(json.dumps(results, indent=2))
        finally:
            try:
                if mapped:
                    run("reverse", "--remove", "tcp:" + port)
            finally:
                try:
                    run("shell", "ime", "set", original_ime)
                finally:
                    server.shutdown()
                    server.server_close()
                    worker.join()


def assert_true(value):
    assert value


if __name__ == "__main__":
    main()
