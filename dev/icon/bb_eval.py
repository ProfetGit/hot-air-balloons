#!/usr/bin/env python3
"""Evaluate JS in the shared Blockbench over the MCP plugin's HTTP endpoint (risky_eval), for when the blockbench MCP
isn't connected to the session. Usage: bb_eval.py '<js expression>'  (promises are awaited by the plugin)."""
import json
import sys
import urllib.request

URL = "http://localhost:3333/bb-mcp"


def call(method, params, sid=None, notify=False):
    body = {"jsonrpc": "2.0", "method": method, "params": params}
    if not notify:
        body["id"] = 1
    req = urllib.request.Request(URL, json.dumps(body).encode(),
                                 {"Content-Type": "application/json", "Accept": "application/json, text/event-stream"})
    if sid:
        req.add_header("Mcp-Session-Id", sid)
    with urllib.request.urlopen(req, timeout=1800) as r:
        sid = r.headers.get("Mcp-Session-Id") or sid
        text = r.read().decode()
    for line in text.splitlines():
        if line.startswith("data:"):
            text = line[5:]
    return sid, (json.loads(text) if text.strip() and not notify else None)


sid, _ = call("initialize", {"protocolVersion": "2025-03-26", "capabilities": {}, "clientInfo": {"name": "habi", "version": "1"}})
call("notifications/initialized", {}, sid, notify=True)
_, res = call("tools/call", {"name": "risky_eval", "arguments": {"code": sys.argv[1]}}, sid)
print(json.dumps(res.get("result", res))[:3000])
