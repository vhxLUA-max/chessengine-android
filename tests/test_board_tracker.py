import unittest

import chess

from board_tracker import BoardTracker, START_FEN


def frame_with_changes(changes=None):
    values = [20.0, 20.0, 20.0, 5.0] * 64

    for square_index, vector in (changes or {}).items():
        start = square_index * 4
        values[start:start + 4] = vector

    return values


def run_until_commit(tracker, session_id, base, changed):
    tracker.process(session_id, base)
    tracker.process(session_id, base)
    tracker.process(session_id, base)

    tracker.process(session_id, changed)
    tracker.process(session_id, changed)
    return tracker.process(session_id, changed)


class BoardTrackerTests(unittest.TestCase):
    def test_white_orientation_detects_e2e4(self):
        tracker = BoardTracker()
        base = frame_with_changes()

        # White orientation: e2 -> screen index 52, e4 -> 36.
        moved = frame_with_changes({
            52: [140.0, 140.0, 140.0, 20.0],
            36: [140.0, 140.0, 140.0, 20.0],
        })

        result = run_until_commit(
            tracker,
            "white-game",
            base,
            moved,
        )

        self.assertTrue(result["changed"])
        self.assertEqual(result["move_uci"], "e2e4")
        self.assertEqual(result["side"], "black")

    def test_black_orientation_maps_screen_to_chess_squares(self):
        tracker = BoardTracker()
        fen = (
            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/"
            "RNBQKBNR b KQkq - 0 1"
        )
        tracker.reset("black-game", fen, "black")
        base = frame_with_changes()

        self.assertEqual(
            chess.square_name(tracker._square_from_index(51, "black")),
            "e7",
        )
        self.assertEqual(
            chess.square_name(tracker._square_from_index(35, "black")),
            "e5",
        )

        # Black orientation: e7 -> screen index 51, e5 -> 35.
        moved = frame_with_changes({
            51: [140.0, 140.0, 140.0, 20.0],
            35: [140.0, 140.0, 140.0, 20.0],
        })

        result = run_until_commit(
            tracker,
            "black-game",
            base,
            moved,
        )

        self.assertTrue(result["changed"])
        self.assertEqual(result["move_uci"], "e7e5")

    def test_position_reset_returns_to_initial_position(self):
        tracker = BoardTracker()
        base = frame_with_changes()

        moved = frame_with_changes({
            52: [140.0, 140.0, 140.0, 20.0],
            36: [140.0, 140.0, 140.0, 20.0],
        })

        first = run_until_commit(
            tracker,
            "reset-game",
            base,
            moved,
        )
        self.assertEqual(first["move_uci"], "e2e4")

        result = None
        for _ in range(3):
            result = tracker.process("reset-game", base)

        self.assertTrue(result["reset_detected"])
        self.assertEqual(result["fen"], START_FEN)

    def test_ambiguous_change_does_not_advance_position(self):
        tracker = BoardTracker()
        base = frame_with_changes()
        ambiguous = frame_with_changes({
            51: [140.0, 140.0, 140.0, 20.0],
            52: [140.0, 140.0, 140.0, 20.0],
            53: [140.0, 140.0, 140.0, 20.0],
        })

        tracker.process("ambiguous-game", base)
        tracker.process("ambiguous-game", base)
        tracker.process("ambiguous-game", base)
        tracker.process("ambiguous-game", ambiguous)
        tracker.process("ambiguous-game", ambiguous)
        result = tracker.process("ambiguous-game", ambiguous)

        self.assertTrue(result["ambiguous"])
        self.assertEqual(result["fen"], START_FEN)


if __name__ == "__main__":
    unittest.main()
