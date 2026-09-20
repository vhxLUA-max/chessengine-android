#!/usr/bin/env python3

import math
import threading

import chess


START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"


class BoardTracker:
    """
    Conservative live board tracker based on per-square visual signatures.

    Android sends four values per square:
    mean R, mean G, mean B, and grayscale variance.

    The tracker never invents a chess move from an arbitrary visual change.
    It waits for stable frames, maps changed screen cells to chess squares,
    and accepts a move only when it uniquely matches a legal move from the
    current board.
    """

    def __init__(self):
        self.sessions = {}
        self.lock = threading.RLock()

    @staticmethod
    def _validate_cells(cells):
        if not isinstance(cells, list) or len(cells) != 256:
            raise ValueError("cells must contain exactly 256 numeric values")

        values = []
        for value in cells:
            try:
                number = float(value)
            except (TypeError, ValueError) as exc:
                raise ValueError("cells must contain numeric values") from exc

            if not math.isfinite(number):
                raise ValueError("cells contain a non-finite value")

            values.append(number)

        return values

    @staticmethod
    def _cell_vector(cells, index):
        start = index * 4
        return cells[start:start + 4]

    @staticmethod
    def _cell_distance(a, b):
        rgb = (
            abs(a[0] - b[0])
            + abs(a[1] - b[1])
            + abs(a[2] - b[2])
        ) / 3.0

        variance = abs(a[3] - b[3])
        return rgb * 0.72 + variance * 0.28

    @classmethod
    def _frame_distance(cls, old_cells, new_cells):
        total = 0.0
        maximum = 0.0

        for index in range(64):
            distance = cls._cell_distance(
                cls._cell_vector(old_cells, index),
                cls._cell_vector(new_cells, index),
            )
            total += distance
            maximum = max(maximum, distance)

        return total / 64.0, maximum

    @classmethod
    def _changed_indices(cls, old_cells, new_cells):
        changed = []

        for index in range(64):
            distance = cls._cell_distance(
                cls._cell_vector(old_cells, index),
                cls._cell_vector(new_cells, index),
            )

            if distance >= 10.0:
                changed.append(index)

        return changed

    @staticmethod
    def _square_from_index(index, orientation):
        row = index // 8
        col = index % 8

        if str(orientation).lower() == "black":
            file_index = 7 - col
            rank_index = row
        else:
            file_index = col
            rank_index = 7 - row

        return chess.square(file_index, rank_index)

    @classmethod
    def _expected_squares(cls, board, move):
        squares = {move.from_square, move.to_square}

        if board.is_castling(move):
            rank = chess.square_rank(move.from_square)

            if chess.square_file(move.to_square) > chess.square_file(move.from_square):
                rook_from = chess.square(7, rank)
                rook_to = chess.square(5, rank)
            else:
                rook_from = chess.square(0, rank)
                rook_to = chess.square(3, rank)

            squares.update((rook_from, rook_to))

        elif board.is_en_passant(move):
            captured = chess.square(
                chess.square_file(move.to_square),
                chess.square_rank(move.from_square),
            )
            squares.add(captured)

        return squares

    @classmethod
    def _infer_move(cls, board, changed_squares):
        changed = set(changed_squares)

        if len(changed) < 2 or len(changed) > 10:
            return None, True, []

        exact = []

        for move in board.legal_moves:
            expected = cls._expected_squares(board, move)

            if expected == changed:
                exact.append(move)

        if len(exact) == 1:
            return exact[0], False, []

        if len(exact) > 1:
            return None, True, [move.uci() for move in exact[:8]]

        candidates = []

        for move in board.legal_moves:
            expected = cls._expected_squares(board, move)

            if move.from_square not in changed or move.to_square not in changed:
                continue

            overlap = len(expected & changed)
            extras = len(changed - expected)

            if overlap != len(expected):
                continue

            if extras > 5:
                continue

            score = overlap * 12.0 - extras * 1.5

            if extras <= 2:
                score += 8.0

            candidates.append((score, move))

        if not candidates:
            return None, True, []

        candidates.sort(key=lambda item: item[0], reverse=True)

        best_score, best_move = candidates[0]
        second_score = candidates[1][0] if len(candidates) > 1 else -999.0

        if best_score < 18.0:
            return None, True, [move.uci() for _, move in candidates[:8]]

        if len(candidates) > 1 and best_score - second_score < 5.0:
            return None, True, [move.uci() for _, move in candidates[:8]]

        return best_move, False, []

    def _new_session(self, session_id, fen, orientation):
        board = chess.Board(fen)
        return {
            "board": board,
            "initial_fen": board.fen(),
            "orientation": orientation or "white",
            "last_frame": None,
            "initial_cells": None,
            "stable_count": 0,
            "committed_cells": None,
            "last_move": None,
            "move_number": 0,
        }

    def reset(self, session_id, fen=START_FEN, orientation="white"):
        try:
            board = chess.Board(fen)
        except ValueError as exc:
            raise ValueError("invalid FEN: " + str(exc)) from exc

        with self.lock:
            state = self._new_session(session_id, board.fen(), orientation)
            self.sessions[str(session_id)] = state
            return state["board"].fen()

    def set_fen(self, session_id, fen, orientation="white"):
        return self.reset(session_id, fen, orientation)

    @staticmethod
    def _mirrored_cells(cells):
        mirrored = []
        for index in range(63, -1, -1):
            mirrored.extend(cells[index * 4:(index + 1) * 4])
        return mirrored

    def process(self, session_id, cells, initial_fen=START_FEN, orientation="white"):
        values = self._validate_cells(cells)
        session_id = str(session_id)

        with self.lock:
            state = self.sessions.get(session_id)

            if state is None:
                self.reset(session_id, initial_fen or START_FEN, orientation)
                state = self.sessions[session_id]

            state["orientation"] = orientation or state["orientation"]

            if state["initial_cells"] is None:
                state["initial_cells"] = list(values)

            if state["last_frame"] is None:
                state["last_frame"] = values
                return self._response(state)

            direct_average, direct_maximum = self._frame_distance(
                state["last_frame"],
                values,
            )
            mirrored_average, mirrored_maximum = self._frame_distance(
                state["last_frame"],
                self._mirrored_cells(values),
            )

            if (
                direct_average > 8.0
                and mirrored_average <= 4.0
                and mirrored_maximum <= 25.0
            ):
                state["orientation"] = (
                    "black"
                    if state["orientation"].lower() == "white"
                    else "white"
                )
                state["last_frame"] = values
                state["committed_cells"] = values
                state["initial_cells"] = list(values)
                state["stable_count"] = 0

                return self._response(
                    state,
                    orientation_detected=True,
                )

            average, maximum = direct_average, direct_maximum
            state["last_frame"] = values

            # A visual move may be animated. Do not inspect it until the
            # board has produced two consecutive stable frames.
            if average <= 4.0 and maximum <= 25.0:
                state["stable_count"] += 1
            else:
                state["stable_count"] = 0
                return self._response(state)

            if state["stable_count"] < 2:
                return self._response(state)

            if state["committed_cells"] is None:
                state["committed_cells"] = values
                return self._response(state)

            initial_average, initial_maximum = self._frame_distance(
                state["initial_cells"],
                values,
            )

            if (
                state["board"].fen() != state["initial_fen"]
                and initial_average <= 4.0
                and initial_maximum <= 25.0
            ):
                state["board"] = chess.Board(state["initial_fen"])
                state["committed_cells"] = values
                state["stable_count"] = 0
                state["last_move"] = None
                state["move_number"] = 0

                return self._response(
                    state,
                    reset_detected=True,
                )

            changed_indices = self._changed_indices(
                state["committed_cells"],
                values,
            )

            if not changed_indices:
                return self._response(state)

            changed_squares = {
                self._square_from_index(index, state["orientation"])
                for index in changed_indices
            }

            move, ambiguous, candidates = self._infer_move(
                state["board"],
                changed_squares,
            )

            if move is None:
                # Keep the old committed position. Waiting is safer than
                # advancing the game state from an uncertain screenshot.
                return self._response(
                    state,
                    ambiguous=ambiguous,
                    changed_squares=changed_squares,
                    candidates=candidates,
                )

            previous_fen = state["board"].fen()
            san = state["board"].san(move)
            state["board"].push(move)
            state["committed_cells"] = values
            state["stable_count"] = 0
            state["last_move"] = move.uci()
            state["move_number"] += 1

            return self._response(
                state,
                changed=True,
                move_uci=move.uci(),
                move_san=san,
                previous_fen=previous_fen,
                changed_squares=changed_squares,
            )

    @staticmethod
    def _response(
        state,
        changed=False,
        ambiguous=False,
        move_uci=None,
        move_san=None,
        previous_fen=None,
        changed_squares=None,
        candidates=None,
        reset_detected=False,
        orientation_detected=False,
    ):
        board = state["board"]

        payload = {
            "ok": True,
            "changed": bool(changed),
            "ambiguous": bool(ambiguous),
            "fen": board.fen(),
            "side": "white" if board.turn == chess.WHITE else "black",
            "move_uci": move_uci,
            "move_san": move_san,
            "last_move_uci": state["last_move"],
            "move_number": state["move_number"],
            "game_over": board.is_game_over(),
            "check": board.is_check(),
            "checkmate": board.is_checkmate(),
            "stalemate": board.is_stalemate(),
            "changed_squares": sorted(
                chess.square_name(square)
                for square in (changed_squares or set())
            ),
            "reset_detected": bool(reset_detected),
            "orientation_detected": bool(orientation_detected),
        }

        if previous_fen is not None:
            payload["previous_fen"] = previous_fen

        if candidates:
            payload["candidates"] = candidates

        return payload
