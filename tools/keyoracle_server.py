#!/usr/bin/env python3
"""Loopback-only, opt-in browser event collector for the emkeys test page.

Run this, adb reverse tcp:8777 tcp:8777, then open
http://localhost:8777/keyoracle.html?report=1 on the tablet. GET /events returns
only events from that test page. POST /reset clears the in-memory test trace.
No third-party packages; nothing is persisted by this server.
"""

import argparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
from threading import Lock
from urllib.parse import urlsplit


class OracleServer(ThreadingHTTPServer):
    def __init__(self, port=8777):
        super().__init__(("127.0.0.1", port), OracleHandler)
        self.events = []
        self.lock = Lock()


class OracleHandler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def reply(self, status, body, kind="application/json"):
        self.send_response(status)
        self.send_header("Content-Type", kind + "; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        path = urlsplit(self.path).path
        if path in ("/", "/keyoracle.html"):
            self.reply(200, Path(__file__).with_name("keyoracle.html").read_bytes(), "text/html")
        elif path == "/events":
            with self.server.lock:
                events = sorted(self.server.events, key=lambda event: event["n"])
            self.reply(200, json.dumps(events).encode())
        else:
            self.reply(404, b"{}")

    def do_POST(self):
        # Reject cross-origin submissions; the fixture uses a same-origin fetch.
        if self.headers.get("Sec-Fetch-Site") == "cross-site":
            self.reply(403, b"{}")
            return
        length = int(self.headers.get("Content-Length", "0"))
        if not 0 <= length <= 65536:
            self.reply(413, b"{}")
            return
        body = self.rfile.read(length)
        path = urlsplit(self.path).path
        if path == "/reset":
            with self.server.lock:
                self.server.events.clear()
        elif path == "/events":
            try:
                event = json.loads(body)
                if not isinstance(event, dict) or not isinstance(event.get("n"), int):
                    raise ValueError("Invalid event")
            except (ValueError, TypeError):
                self.reply(400, b"{}")
                return
            with self.server.lock:
                self.server.events.append(event)
                self.server.events[:] = self.server.events[-5000:]
        else:
            self.reply(404, b"{}")
            return
        self.reply(200, b"{}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=8777)
    args = parser.parse_args()
    server = OracleServer(args.port)
    print(f"Oracle: http://localhost:{server.server_port}/keyoracle.html?report=1", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
