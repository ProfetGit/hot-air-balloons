"""Run a JS file in the shared Blockbench over the MCP plugin's HTTP endpoint (risky_eval), holding BB_LOCK."""
import json
import sys
import urllib.request
from pathlib import Path

URL = "http://localhost:3333/bb-mcp"


def call(method, params, sid=None, notify=False):
    body = {"jsonrpc": "2.0", "method": method, "params": params}
    if not notify:
        body["id"] = 1
    req = urllib.request.Request(URL, json.dumps(body).encode(), {"Content-Type": "application/json", "Accept": "application/json, text/event-stream"})
    if sid:
        req.add_header("Mcp-Session-Id", sid)
    with urllib.request.urlopen(req, timeout=120) as r:
        sid = r.headers.get("Mcp-Session-Id") or sid
        text = r.read().decode()
    for line in text.splitlines():
        if line.startswith("data:"):
            text = line[5:]
    return sid, (json.loads(text) if text.strip() and not notify else None)


def main():
    path = Path(sys.argv[1]).resolve()
    sid, _ = call("initialize", {"protocolVersion": "2025-03-26", "capabilities": {}, "clientInfo": {"name": "hab-bb", "version": "1"}})
    call("notifications/initialized", {}, sid, notify=True)
    code = ("(window.BB_LOCK && window.BB_LOCK.until > Date.now() && window.BB_LOCK.owner !== 'hab') ? {busy: window.BB_LOCK} : "
            f"(window.BB_LOCK = {{owner: 'hab', until: Date.now() + 60000}}, eval(require('fs').readFileSync({json.dumps(str(path))}, 'utf8')))")
    _, res = call("tools/call", {"name": "risky_eval", "arguments": {"code": code}}, sid)
    print(json.dumps(res.get("result", res))[:400])


if __name__ == "__main__":
    main()
