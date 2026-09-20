#!/usr/bin/env python3

import json
import urllib.request

URL = "http://127.0.0.1:8765/analyze"

payload = {
    "fen": "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
    "depth": 12,
    "multipv": 3,
}

request = urllib.request.Request(
    URL,
    data=json.dumps(payload).encode("utf-8"),
    headers={"Content-Type": "application/json"},
    method="POST",
)

with urllib.request.urlopen(request, timeout=60) as response:
    result = json.loads(response.read())

print(json.dumps(result, indent=2))
