#!/usr/bin/env python3

import argparse
import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse

from board_tracker import BoardTracker, START_FEN
from engine_server import EngineService

ROOT = os.path.dirname(os.path.abspath(__file__))
DEFAULT_CONFIG = os.path.join(ROOT, "config.json")


class Handler(BaseHTTPRequestHandler):
    server_version = "CheezieTermuxEngine/1.1"

    def send_json(self, status, payload):
        raw = json.dumps(payload, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.end_headers()
        if status != 204:
            self.wfile.write(raw)

    def authorized(self):
        token = self.server.auth_token
        if not token:
            return True
        return self.headers.get("Authorization", "") == "Bearer " + token

    def read_json(self):
        length = int(self.headers.get("Content-Length", "0"))
        return json.loads(self.rfile.read(length) or b"{}")

    def do_OPTIONS(self):
        self.send_json(204, {})

    def do_GET(self):
        path = urlparse(self.path).path

        if path == "/health":
            service = self.server.engine_service
            self.send_json(200, {
                "ok": True,
                "service": "cheeezie-termux-engine",
                "engine": os.path.basename(service.engine_path),
                "engine_running": service.engine is not None,
                "sessions": len(self.server.board_tracker.sessions),
            })
            return

        self.send_json(404, {"ok": False, "error": "not found"})

    def do_POST(self):
        if not self.authorized():
            self.send_json(401, {"ok": False, "error": "unauthorized"})
            return

        path = urlparse(self.path).path

        try:
            body = self.read_json()
        except (ValueError, json.JSONDecodeError):
            self.send_json(400, {"ok": False, "error": "invalid JSON"})
            return

        try:
            if path == "/set-position":
                session_id = str(body.get("session_id", "android-main"))
                fen = body.get("fen", START_FEN)
                orientation = body.get("orientation", "white")
                fen = self.server.board_tracker.set_fen(
                    session_id,
                    fen,
                    orientation,
                )
                self.server.analysis_cache.pop(session_id, None)
                self.send_json(200, {
                    "ok": True,
                    "fen": fen,
                    "side": "white" if " w " in fen else "black",
                })
                return

            if path == "/detect":
                session_id = str(body.get("session_id", "android-main"))

                result = self.server.board_tracker.process(
                    session_id=session_id,
                    cells=body.get("cells"),
                    initial_fen=body.get("initial_fen", START_FEN),
                    orientation=body.get("orientation", "white"),
                )

                should_analyze = (
                    session_id not in self.server.analysis_cache
                    or result["changed"]
                )

                if should_analyze and not result["ambiguous"]:
                    analysis = self.server.engine_service.analyze({
                        "fen": result["fen"],
                        "depth": body.get("depth", 12),
                        "movetime": body.get("movetime"),
                        "nodes": body.get("nodes"),
                        "multipv": body.get("multipv", 5),
                        "threads": body.get("threads", 2),
                        "hash": body.get("hash", 128),
                    })
                    self.server.analysis_cache[session_id] = result["fen"]
                    result["lines"] = analysis["lines"]

                self.send_json(200, result)
                return

            if path == "/analyze":
                result = self.server.engine_service.analyze(body)
                self.send_json(200, result)
                return

            if path == "/bestmove":
                result = self.server.engine_service.analyze(body)
                first = result["lines"][0] if result["lines"] else {}
                self.send_json(200, {
                    "ok": result["ok"],
                    "fen": result["fen"],
                    "side": result["side"],
                    "bestmove_uci": first.get("bestmove_uci"),
                    "bestmove_san": first.get("bestmove_san"),
                    "score_cp": first.get("score_cp"),
                    "mate": first.get("mate"),
                    "depth": first.get("depth"),
                })
                return

            self.send_json(404, {"ok": False, "error": "not found"})

        except ValueError as exc:
            self.send_json(400, {"ok": False, "error": str(exc)})
        except Exception as exc:
            self.send_json(500, {"ok": False, "error": str(exc)})

    def log_message(self, fmt, *args):
        print("[http] " + self.address_string() + " - " + (fmt % args))


def main():
    parser = argparse.ArgumentParser(description="Cheezie Android engine server")
    parser.add_argument("--config", default=DEFAULT_CONFIG)
    parser.add_argument("--host", default=None)
    parser.add_argument("--port", type=int, default=None)
    parser.add_argument("--token", default=None)

    args = parser.parse_args()

    with open(args.config, "r", encoding="utf-8") as handle:
        config = json.load(handle)

    host = args.host if args.host is not None else config.get("host", "127.0.0.1")
    port = args.port if args.port is not None else int(config.get("port", 8765))
    token = args.token if args.token is not None else config.get("token", "")

    engine = EngineService(
        config.get("engine_path", "engine/stockfish"),
        threads=config.get("threads", 2),
        hash_mb=config.get("hash", 128),
        default_multipv=config.get("multipv", 5),
    )
    engine.start()

    httpd = ThreadingHTTPServer((host, port), Handler)
    httpd.engine_service = engine
    httpd.board_tracker = BoardTracker()
    httpd.analysis_cache = {}
    httpd.auth_token = token

    print("Cheezie Android Engine listening on http://" + host + ":" + str(port))
    print("Stockfish backend: " + engine.engine_path)
    print("Press Ctrl+C to stop.")

    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping...")
    finally:
        httpd.server_close()
        engine.stop()


if __name__ == "__main__":
    main()
