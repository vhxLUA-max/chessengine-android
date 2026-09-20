#!/usr/bin/env python3

import base64
from dataclasses import dataclass
from io import BytesIO

import chess
from PIL import Image, ImageStat, ImageFilter


START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"


@dataclass
class SessionState:
    board: chess.Board
    stable: Image.Image | None = None
    last_affected: set[str] | None = None
    last_move: str | None = None
    orientation: str = "white"


class BoardTracker:
    def __init__(self, threshold=13.0):
        self.threshold = float(threshold)
        self.sessions: dict[str, SessionState] = {}

    def reset(self, session_id, fen=START_FEN, orientation="white"):
        board = chess.Board(fen)
        self.sessions[session_id] = SessionState(
            board=board,
            stable=None,
            last_affected=None,
            last_move=None,
            orientation=orientation,
        )

    def _decode(self, image_b64):
        raw = base64.b64decode(image_b64, validate=True)
        image = Image.open(BytesIO(raw)).convert("RGB")
        image.thumbnail((256, 256), Image.Resampling.BILINEAR)
        image = image.resize((256, 256), Image.Resampling.BILINEAR)
        image = image.filter(ImageFilter.BoxBlur(0.7))
        return image

    @staticmethod
    def _square_box(file_index, rank_index):
        size = 256 // 8
        return (
            file_index * size + int(size * 0.16),
            rank_index * size + int(size * 0.16),
            (file_index + 1) * size - int(size * 0.16),
            (rank_index + 1) * size - int(size * 0.16),
        )

    @staticmethod
    def _difference(a, b):
        stat = ImageStat.Stat(
            Image.new("RGB", a.size)
        )
        del stat

        pixels_a = a.load()
        pixels_b = b.load()

        total = 0.0
        count = 0

        for y in range(0, a.height, 5):
            for x in range(0, a.width, 5):
                pa = pixels_a[x, y]
                pb = pixels_b[x, y]
                total += abs(pa[0] - pb[0])
                total += abs(pa[1] - pb[1])
                total += abs(pa[2] - pb[2])
                count += 3

        return total / max(1, count)

    def _changed_squares(self, old, new):
        changed = set()

        for rank in range(8):
            for file_index in range(8):
                box = self._square_box(file_index, rank)
                a = old.crop(box)
                b = new.crop(box)
                diff = self._difference(a, b)

                if diff >= self.threshold:
                    square = chr(ord("a") + file_index) + str(8 - rank)
                    changed.add(square)

        return changed

    @staticmethod
    def _affected_squares(board, move):
        affected = {chess.square_name(move.from_square), chess.square_name(move.to_square)}

        if board.is_en_passant(move):
            capture_square = chess.square(
                chess.square_file(move.to_square),
                chess.square_rank(move.from_square),
            )
            affected.add(chess.square_name(capture_square))

        if board.is_castling(move):
            rank = chess.square_rank(move.from_square)

            if chess.square_file(move.to_square) > chess.square_file(move.from_square):
                rook_from = chess.square(7, rank)
                rook_to = chess.square(5, rank)
            else:
                rook_from = chess.square(0, rank)
                rook_to = chess.square(3, rank)

            affected.add(chess.square_name(rook_from))
            affected.add(chess.square_name(rook_to))

        return affected

    def _infer_move(self, state, changed):
        if not changed:
            return None, None

        legal = list(state.board.legal_moves)
        previous = state.last_affected or set()
        candidates = []

        for move in legal:
            affected = self._affected_squares(state.board, move)

            if affected == changed:
                candidates.append((move, affected, 100))
                continue

            # Previous-move highlight changes can add the old move squares.
            if affected.issubset(changed) and changed - affected <= previous:
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

        best = candidates[0]
        if len(candidates) > 1:
            top_score = best[2]
            top = [c for c in candidates if c[2] == top_score]
            if len(top) > 1:
                return None, sorted(m.uci() for m, _, _ in top)

        return best[0], best[1]

    def process(self, session_id, image_b64, initial_fen=START_FEN, orientation="white"):
        if session_id not in self.sessions:
            self.reset(session_id, initial_fen or START_FEN, orientation)

        state = self.sessions[session_id]
        state.orientation = orientation or state.orientation

        image = self._decode(image_b64)

        if state.stable is None:
            state.stable = image
            return {
                "ok": True,
                "changed": False,
                "initialized": True,
                "ambiguous": False,
                "fen": state.board.fen(),
                "side": "white" if state.board.turn == chess.WHITE else "black",
                "last_move": None,
                "changed_squares": [],
            }

        changed = self._changed_squares(state.stable, image)

        if not changed:
            return {
                "ok": True,
                "changed": False,
                "initialized": True,
                "ambiguous": False,
                "fen": state.board.fen(),
                "side": "white" if state.board.turn == chess.WHITE else "black",
                "last_move": state.last_move,
                "changed_squares": [],
            }

        move, affected_or_candidates = self._infer_move(state, changed)

        if move is None:
            return {
                "ok": True,
                "changed": False,
                "initialized": True,
                "ambiguous": bool(affected_or_candidates),
                "candidates": affected_or_candidates or [],
                "fen": state.board.fen(),
                "side": "white" if state.board.turn == chess.WHITE else "black",
                "last_move": state.last_move,
                "changed_squares": sorted(changed),
            }

        state.board.push(move)
        state.stable = image
        state.last_affected = affected_or_candidates
        state.last_move = move.uci()

        return {
            "ok": True,
            "changed": True,
            "initialized": True,
            "ambiguous": False,
            "fen": state.board.fen(),
            "side": "white" if state.board.turn == chess.WHITE else "black",
            "last_move": move.uci(),
            "changed_squares": sorted(changed),
        }

    def set_fen(self, session_id, fen, orientation="white"):
        self.reset(session_id, fen, orientation)
        return self.sessions[session_id].board.fen()
