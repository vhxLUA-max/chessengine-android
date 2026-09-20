#!/usr/bin/env python3

import argparse
import json
import os
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse

import chess
import chess.engine

ROOT = os.path.dirname(os.path.abspath(__file__))
DEFAULT_CONFIG = os.path.join(ROOT, "config.json")


class EngineService:
    def __init__(self, engine_path, threads=2, hash_mb=128, default_multipv=3):
        self.engine_path = (
            os.path.abspath(os.path.join(ROOT, engine_path))
            if not os.path.isabs(engine_path)
            else engine_path
        )
        self.default_threads = max(1, int(threads))
        self.default_hash = max(1, int(hash_mb))
        self.default_multipv = max(1, int(default_multipv))
        self.lock = threading.RLock()
        self.engine = None

    def start(self):
        with self.lock:
            if self.engine is not None:
                return

            if not os.path.isfile(self.engine_path):
                raise FileNotFoundError(
                    "Engine binary not found: " + self.engine_path
                )

            self.engine = chess.engine.SimpleEngine.popen_uci(self.engine_path)
            self._configure(self.default_threads, self.default_hash)

    def stop(self):
        with self.lock:
            if self.engine is None:
                return

            try:
                self.engine.quit()
            except Exception:
                pass

            self.engine = None

    def _configure(self, threads, hash_mb):
        if self.engine is None:
            return

        options = {}
        available = self.engine.options

        if "Threads" in available:
            options["Threads"] = max(1, min(int(threads), 128))

        if "Hash" in available:
            options["Hash"] = max(1, min(int(hash_mb), 4096))

        if options:
            self.engine.configure(options)

    @staticmethod
    def _limit(body):
        if body.get("movetime") is not None:
            milliseconds = max(1, min(int(body["movetime"]), 120000))
            return chess.engine.Limit(time=milliseconds / 1000.0)

        if body.get("nodes") is not None:
            nodes = max(1, min(int(body["nodes"]), 200000000))
            return chess.engine.Limit(nodes=nodes)

        depth = max(1, min(int(body.get("depth", 12)), 30))
        return chess.engine.Limit(depth=depth)

    @staticmethod
    def _verified_pv(board, pv):
        replay = board.copy()
        pv_uci = []
        pv_san = []

        for move in pv or []:
            if move not in replay.legal_moves:
                break

            pv_uci.append(move.uci())
            pv_san.append(replay.san(move))
            replay.push(move)

        return pv_uci, pv_san

    @staticmethod
    def _score(info, board):
        score = info.get("score")
        if score is None:
            return None, None

        pov = score.pov(board.turn)
        mate = pov.mate()

        if mate is not None:
            return None, int(mate)

        cp = pov.score(mate_score=100000)
        return (int(cp) if cp is not None else None), None

    def analyze(self, body):
        fen = body.get("fen")

        if not isinstance(fen, str) or not fen.strip():
            raise ValueError("fen is required")

        try:
            board = chess.Board(fen)
        except ValueError as exc:
            raise ValueError("invalid FEN: " + str(exc)) from exc

        multipv = max(1, min(int(body.get("multipv", self.default_multipv)), 10))
        threads = max(1, min(int(body.get("threads", self.default_threads)), 128))
        hash_mb = max(1, min(int(body.get("hash", self.default_hash)), 4096))
        limit = self._limit(body)

        with self.lock:
            self.start()
            self._configure(threads, hash_mb)

            infos = self.engine.analyse(
                board,
                limit,
                multipv=multipv,
            )

        if isinstance(infos, dict):
            infos = [infos]

        lines = []

        for index, info in enumerate(infos, start=1):
            pv_uci, pv_san = self._verified_pv(board, info.get("pv") or [])
            score_cp, mate = self._score(info, board)

            lines.append(
                {
                    "multipv": index,
                    "depth": info.get("depth"),
                    "seldepth": info.get("seldepth"),
                    "score_cp": score_cp,
                    "mate": mate,
                    "bestmove_uci": pv_uci[0] if pv_uci else None,
                    "bestmove_san": pv_san[0] if pv_san else None,
                    "pv_uci": pv_uci,
                    "pv_san": pv_san,
                    "nodes": info.get("nodes"),
                    "nps": info.get("nps"),
                    "time_ms": round((info.get("time") or 0) * 1000),
                }
            )

        return {
            "ok": True,
            "fen": board.fen(),
            "side": "white" if board.turn == chess.WHITE else "black",
            "lines": lines,
        }


class APIHandler(BaseHTTPRequestHandler):
    server_version = "CheezieTermuxEngine/1.0"

    def _json(self, status, payload):
        body = json.dumps(payload, separators=(",", ":")).encode("utf-8")

        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header(
            "Access-Control-Allow-Headers",
            "Content-Type, Authorization",
        )
        self.send_header(
            "Access-Control-Allow-Methods",
            "GET, POST, OPTIONS",
        )
        self.end_headers()

        if status != 204:
            self.wfile.write(body)

    def _authorized(self):
        token = self.server.auth_token

        if not token:
            return True

        return (
            self.headers.get("Authorization", "")
            == "Bearer " + token
        )

    def do_OPTIONS(self):
        self._json(204, {})

    def do_GET(self):
        path = urlparse(self.path).path

        if path == "/health":
            service = self.server.engine_service

            self._json(
                200,
                {
                    "ok": True,
                    "service": "cheeezie-termux-engine",
                    "engine": os.path.basename(service.engine_path),
                    "engine_running": service.engine is not None,
                },
            )
            return

        self._json(404, {"ok": False, "error": "not found"})

    def do_POST(self):
        if not self._authorized():
            self._json(
                401,
                {"ok": False, "error": "unauthorized"},
            )
            return

        path = urlparse(self.path).path

        if path not in ("/analyze", "/bestmove"):
            self._json(404, {"ok": False, "error": "not found"})
            return

        try:
            length = int(self.headers.get("Content-Length", "0"))
            raw = self.rfile.read(length)
            body = json.loads(raw or b"{}")
        except (ValueError, json.JSONDecodeError):
            self._json(400, {"ok": False, "error": "invalid JSON"})
            return

        try:
            result = self.server.engine_service.analyze(body)
        except ValueError as exc:
            self._json(400, {"ok": False, "error": str(exc)})
            return
        except Exception as exc:
            self._json(500, {"ok": False, "error": str(exc)})
            return

        if path == "/bestmove":
            first = result["lines"][0] if result["lines"] else {}

            result = {
                "ok": result["ok"],
                "fen": result["fen"],
                "side": result["side"],
                "bestmove_uci": first.get("bestmove_uci"),
                "bestmove_san": first.get("bestmove_san"),
                "score_cp": first.get("score_cp"),
                "mate": first.get("mate"),
                "depth": first.get("depth"),
            }

        self._json(200, result)

    def log_message(self, fmt, *args):
        print("[http] " + self.address_string() + " - " + (fmt % args))


def load_config(path):
    with open(path, "r", encoding="utf-8") as handle:
        return json.load(handle)


def main():
    parser = argparse.ArgumentParser(
        description="Cheezie Termux Engine"
    )
    parser.add_argument("--config", default=DEFAULT_CONFIG)
    parser.add_argument("--host", default=None)
    parser.add_argument("--port", type=int, default=None)
    parser.add_argument("--token", default=None)

    args = parser.parse_args()
    config = load_config(args.config)

    host = (
        args.host
        if args.host is not None
        else config.get("host", "127.0.0.1")
    )

    port = (
        args.port
        if args.port is not None
        else int(config.get("port", 8765))
    )

    token = (
        args.token
        if args.token is not None
        else config.get("token", "")
    )

    service = EngineService(
        config.get("engine_path", "engine/stockfish"),
        threads=config.get("threads", 2),
        hash_mb=config.get("hash", 128),
        default_multipv=config.get("multipv", 3),
    )

    service.start()

    httpd = ThreadingHTTPServer((host, port), APIHandler)
    httpd.engine_service = service
    httpd.auth_token = token

    print("Cheezie Termux Engine listening on http://" + host + ":" + str(port))
    print("Engine: " + service.engine_path)
    print(
        "Threads: "
        + str(config.get("threads", 2))
        + " | Hash: "
        + str(config.get("hash", 128))
        + " MB | MultiPV: "
        + str(config.get("multipv", 3))
    )
    print("Press Ctrl+C to stop.")

    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping...")
    finally:
        httpd.server_close()
        service.stop()


if __name__ == "__main__":
    main()
