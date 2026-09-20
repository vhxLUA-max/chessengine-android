#!/usr/bin/env python3

import threading
from dataclasses import dataclass

import chess

START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"


@dataclass
class Session:
    board: chess.Board
    stable: list[float] | None = None
    last_affected: set[str] | None = None
    last_move: str | None = None
    orientation: str = "white"


class BoardTracker:
    def __init__(self, threshold: float = 16.0):
        self.threshold = float(threshold)
        self.sessions: dict[str, Session] = {}
        self.lock = threading.RLock()

    def reset(self, session_id: str, fen: str = START_FEN, orientation: str = "white"):
        board = chess.Board(fen)
        with self.lock:
            self.sessions[session_id] = Session(
                board=board,
                orientation=orientation or "white",
            )

    @staticmethod
    def _diff(a, b):
        rgb = (
            abs(a[0] - b[0])
            + abs(a[1] - b[1])
            + abs(a[2] - b[2])
        ) / 3.0
        variance = abs(a[3] - b[3]) ** 0.5
        return rgb + variance * 0.20

    def _changed(self, old, new):
        result = set()

        for i in range(64):
            a = old[i * 4:(i + 1) * 4]
            b = new[i * 4:(i + 1) * 4]

            if self._diff(a, b) >= self.threshold:
                file_index = i % 8
                rank_from_top = i // 8
                result.add(chr(ord("a") + file_index) + str(8 - rank_from_top))

        return result

    @staticmethod
    def _affected(board, move):
        squares = {
            chess.square_name(move.from_square),
            chess.square_name(move.to_square),
        }

        if board.is_en_passant(move):
            capture_square = chess.square(
                chess.square_file(move.to_square),
                chess.square_rank(move.from_square),
            )
            squares.add(chess.square_name(capture_square))

        if board.is_castling(move):
            rank = chess.square_rank(move.from_square)

            if chess.square_file(move.to_square) > chess.square_file(move.from_square):
                rook_from = chess.square(7, rank)
                rook_to = chess.square(5, rank)
            else:
                rook_from = chess.square(0, rank)
                rook_to = chess.square(3, rank)

            squares.add(chess.square_name(rook_from))
            squares.add(chess.square_name(rook_to))

        return squares

    def _infer(self, session, changed):
        if not changed:
            return None, None

        old_highlight = session.last_affected or set()
        candidates = []

        for move in session.board.legal_moves:
            affected = self._affected(session.board, move)

            if affected == changed:
                candidates.append((move, affected, 100))
            elif affected.issubset(changed) and changed - affected <= old_highlight:
                candidates.append((move, affected, 90))

        if not candidates:
            return None, None

        candidates.sort(
            key=lambda item: (
                -item[2],
                0 if item[0].promotion == chess.QUEEN else 1,
                item[0].uci(),
            )
        )

        score = candidates[0][2]
        top = [item for item in candidates if item[2] == score]

        if len(top) > 1:
            return None, sorted(item[0].uci() for item in top)

        return candidates[0][0], candidates[0][1]

    @staticmethod
    def _response(session, changed=False, ambiguous=False, squares=None, candidates=None):
        payload = {
            "ok": True,
            "changed": changed,
            "ambiguous": ambiguous,
            "fen": session.board.fen(),
            "side": "white" if session.board.turn == chess.WHITE else "black",
            "last_move": session.last_move,
            "changed_squares": sorted(squares or []),
        }

        if candidates:
            payload["candidates"] = candidates

        return payload

    def process(self, session_id, cells, initial_fen=START_FEN, orientation="white"):
        if not isinstance(cells, list) or len(cells) != 256:
            raise ValueError("cells must contain exactly 256 numbers")

        normalized = [float(value) for value in cells]

        with self.lock:
            if session_id not in self.sessions:
                self.reset(session_id, initial_fen or START_FEN, orientation)

            session = self.sessions[session_id]
            session.orientation = orientation or session.orientation

            if session.stable is None:
                session.stable = normalized
                return self._response(session)

            changed = self._changed(session.stable, normalized)

            if not changed:
                return self._response(session)

            move, extra = self._infer(session, changed)

            if move is None:
                return self._response(
                    session,
                    ambiguous=bool(extra),
                    squares=changed,
                    candidates=extra,
                )

            affected = extra
            session.board.push(move)
            session.stable = normalized
            session.last_affected = affected
            session.last_move = move.uci()

            return self._response(
                session,
                changed=True,
                squares=changed,
            )

    def set_fen(self, session_id, fen, orientation="white"):
        self.reset(session_id, fen, orientation)
        with self.lock:
            return self.sessions[session_id].board.fen()
