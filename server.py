#!/usr/bin/env python3

import argparse
import json
import math
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse

import chess

from board_tracker import BoardTracker, START_FEN
from engine_server import EngineService

ROOT = os.path.dirname(os.path.abspath(__file__))
DEFAULT_CONFIG = os.path.join(ROOT, "config.json")


class Handler(BaseHTTPRequestHandler):
    server_version = "CheezieTermuxEngine/1.2"

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
            if path in ("/set-position", "/reset"):
                session_id = str(body.get("session_id", "android-main"))
                fen = self.server.board_tracker.set_fen(
                    session_id,
                    body.get("fen", START_FEN),
                    body.get("orientation", "white"),
                )
                self.server.analysis_cache.pop(session_id, None)
                board = chess.Board(fen)

                self.send_json(200, {
                    "ok": True,
                    "fen": fen,
                    "side": "white" if board.turn == chess.WHITE else "black",
                })
                return

            if path == "/detect":
                return self._handle_detect(body)

            if path == "/analyze":
                self.send_json(200, self.server.engine_service.analyze(body))
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
                    "score_cp_white": first.get("score_cp_white"),
                    "mate_white": first.get("mate_white"),
                    "depth": first.get("depth"),
                })
                return

            self.send_json(404, {"ok": False, "error": "not found"})

        except ValueError as exc:
            self.send_json(400, {"ok": False, "error": str(exc)})
        except Exception as exc:
            self.send_json(500, {"ok": False, "error": str(exc)})

    def _handle_detect(self, body):
        session_id = str(body.get("session_id", "android-main"))

        result = self.server.board_tracker.process(
            session_id=session_id,
            cells=body.get("cells"),
            initial_fen=body.get("initial_fen", START_FEN),
            orientation=body.get("orientation", "white"),
        )

        cached = self.server.analysis_cache.get(session_id)

        should_analyze = (
            not result["ambiguous"]
            and (
                cached is None
                or cached.get("fen") != result["fen"]
            )
        )

        if should_analyze:
            analysis = self.server.engine_service.analyze({
                "fen": result["fen"],
                "depth": body.get("depth", 12),
                "movetime": body.get("movetime"),
                "nodes": body.get("nodes"),
                "multipv": body.get("multipv", 5),
                "threads": body.get("threads", 2),
                "hash": body.get("hash", 128),
            })

            move_analysis = None
            if result.get("changed") and result.get("move_uci"):
                move_analysis = self._classify_move(
                    result["previous_fen"],
                    result["move_uci"],
                    body,
                )

            cached = {
                "fen": result["fen"],
                "analysis": analysis,
                "move_analysis": move_analysis,
            }
            self.server.analysis_cache[session_id] = cached

        if cached is not None and not result["ambiguous"]:
            analysis = cached["analysis"]
            lines = analysis.get("lines", [])
            first = lines[0] if lines else {}

            result["lines"] = lines
            result["bestmove_uci"] = first.get("bestmove_uci")
            result["bestmove_san"] = first.get("bestmove_san")
            result["score_cp"] = first.get("score_cp")
            result["mate"] = first.get("mate")
            result["score_cp_white"] = first.get("score_cp_white")
            result["mate_white"] = first.get("mate_white")
            result["depth"] = first.get("depth")
            result["move_analysis"] = cached.get("move_analysis")

            result["evaluation"] = {
                "cp": first.get("score_cp"),
                "mate": first.get("mate"),
                "cp_white": first.get("score_cp_white"),
                "mate_white": first.get("mate_white"),
            }

        self.send_json(200, result)

    def _classify_move(self, previous_fen, move_uci, body):
        before = chess.Board(previous_fen)
        move = chess.Move.from_uci(move_uci)

        if move not in before.legal_moves:
            return {
                "ok": False,
                "error": "detected move is not legal in previous position",
            }

        mover = before.turn

        pre_analysis = self.server.engine_service.analyze({
            "fen": previous_fen,
            "depth": body.get("depth", 12),
            "multipv": max(5, min(int(body.get("multipv", 5)), 10)),
            "threads": body.get("threads", 2),
            "hash": body.get("hash", 128),
        })

        best_line = pre_analysis["lines"][0] if pre_analysis["lines"] else {}
        best_value = self._line_score_for_side_to_move(best_line)

        actual_board = before.copy()
        actual_board.push(move)

        post_analysis = self.server.engine_service.analyze({
            "fen": actual_board.fen(),
            "depth": body.get("depth", 12),
            "multipv": 1,
            "threads": body.get("threads", 2),
            "hash": body.get("hash", 128),
        })

        actual_line = post_analysis["lines"][0] if post_analysis["lines"] else {}

        # The post-move score is from the opponent's side to move.
        # Flip it back to the player who made the detected move.
        actual_value = -self._line_score_for_side_to_move(actual_line)

        centipawn_loss = max(
            0,
            int(round(best_value - actual_value)),
        )

        rank = None
        for index, line in enumerate(pre_analysis["lines"], start=1):
            if line.get("bestmove_uci") == move_uci:
                rank = index
                break

        accuracy = max(
            0.0,
            min(100.0, 100.0 * math.exp(-centipawn_loss / 180.0)),
        )

        if rank == 1 and centipawn_loss <= 10:
            classification = "best"
        elif centipawn_loss <= 20:
            classification = "great"
        elif centipawn_loss <= 35:
            classification = "excellent"
        elif centipawn_loss <= 70:
            classification = "good"
        elif centipawn_loss <= 150:
            classification = "inaccuracy"
        elif centipawn_loss <= 300:
            classification = "mistake"
        else:
            classification = "blunder"

        coach = self._coach_message(
            classification,
            pre_analysis["lines"],
        )

        return {
            "ok": True,
            "move_uci": move_uci,
            "side": "white" if mover == chess.WHITE else "black",
            "rank": rank,
            "centipawn_loss": centipawn_loss,
            "accuracy": round(accuracy, 1),
            "classification": classification,
            "bestmove_uci": (
                pre_analysis["lines"][0].get("bestmove_uci")
                if pre_analysis["lines"]
                else None
            ),
            "bestmove_san": (
                pre_analysis["lines"][0].get("bestmove_san")
                if pre_analysis["lines"]
                else None
            ),
            "coach": coach,
        }

    @staticmethod
    def _line_score_for_side_to_move(line):
        mate = line.get("mate")
        if mate is not None:
            return 100000.0 if int(mate) > 0 else -100000.0

        cp = line.get("score_cp")
        if cp is None:
            return 0.0

        return float(cp)

    @staticmethod
    def _coach_message(classification, lines):
        best = lines[0] if lines else {}
        best_move = best.get("bestmove_san") or best.get("bestmove_uci") or "the engine move"

        messages = {
            "best": "Best move. Keep the same plan.",
            "great": "Strong move. The engine agrees with the idea.",
            "excellent": "Very accurate. The position remains under good control.",
            "good": "Good move. There may be a slightly stronger continuation.",
            "inaccuracy": "This works, but the engine prefers " + best_move + ".",
            "mistake": "This gave up noticeable evaluation. Consider " + best_move + " next time.",
            "blunder": "Major evaluation loss. Check forcing replies before committing.",
        }

        return messages.get(
            classification,
            "Review the position for a stronger continuation.",
        )

    def log_message(self, fmt, *args):
        print("[http] " + self.address_string() + " - " + (fmt % args))


def load_config(path):
    with open(path, "r", encoding="utf-8") as handle:
        return json.load(handle)


def main():
    parser = argparse.ArgumentParser(
        description="Cheezie Android engine server"
    )
    parser.add_argument("--config", default=DEFAULT_CONFIG)
    parser.add_argument("--host", default=None)
    parser.add_argument("--port", type=int, default=None)
    parser.add_argument("--token", default=None)

    args = parser.parse_args()
    config = load_config(args.config)

    host = args.host if args.host is not None else config.get("host", "127.0.0.1")
    port = args.port if args.port is not None else int(config.get("port", 8765))
    token = args.token if args.token is not None else config.get("token", "")

    service = EngineService(
        config.get("engine_path", "engine/stockfish"),
        threads=config.get("threads", 2),
        hash_mb=config.get("hash", 128),
        default_multipv=config.get("multipv", 3),
    )
    service.start()

    httpd = ThreadingHTTPServer((host, port), Handler)
    httpd.engine_service = service
    httpd.board_tracker = BoardTracker()
    httpd.analysis_cache = {}
    httpd.auth_token = token

    print(
        "Cheezie Android Engine listening on http://"
        + host
        + ":"
        + str(port)
    )
    print("Engine: " + service.engine_path)
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
