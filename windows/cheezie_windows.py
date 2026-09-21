#!/usr/bin/env python3
import argparse
import os
import tkinter as tk
from tkinter import messagebox, ttk

import chess

from engine_server import EngineService

START_FEN = chess.Board().fen()


class CheezieWindows:
    def __init__(self, root, engine_path):
        self.root = root
        self.root.title("Cheezie Windows")
        self.root.geometry("980x700")
        self.engine_path = engine_path
        self.service = None
        self.board = chess.Board()
        self.selected = None
        self.square_size = 70

        self.fen_var = tk.StringVar(value=START_FEN)
        self.depth_var = tk.IntVar(value=12)
        self.multipv_var = tk.IntVar(value=3)
        self.status_var = tk.StringVar(value="Engine stopped")
        self.eval_var = tk.StringVar(value="Evaluation: --")

        self._build_ui()
        self._draw_board()
        self.root.protocol("WM_DELETE_WINDOW", self._close)

    def _build_ui(self):
        main = ttk.Frame(self.root, padding=10)
        main.pack(fill="both", expand=True)

        left = ttk.Frame(main)
        left.pack(side="left", fill="y")

        self.canvas = tk.Canvas(
            left, width=self.square_size * 8,
            height=self.square_size * 8, highlightthickness=0
        )
        self.canvas.pack()
        self.canvas.bind("<Button-1>", self._board_click)

        right = ttk.Frame(main, padding=(15, 0, 0, 0))
        right.pack(side="left", fill="both", expand=True)

        ttk.Label(right, text="FEN").pack(anchor="w")
        ttk.Entry(right, textvariable=self.fen_var, width=72).pack(fill="x")

        buttons = ttk.Frame(right)
        buttons.pack(fill="x", pady=8)
        ttk.Button(buttons, text="Load FEN", command=self._load_fen).pack(side="left")
        ttk.Button(buttons, text="Reset", command=self._reset).pack(side="left", padx=5)
        ttk.Button(buttons, text="Analyze", command=self._analyze).pack(side="left")

        settings = ttk.LabelFrame(right, text="Engine", padding=8)
        settings.pack(fill="x", pady=8)

        ttk.Label(settings, text="Depth").grid(row=0, column=0, sticky="w")
        ttk.Spinbox(settings, from_=1, to=30, textvariable=self.depth_var, width=8).grid(
            row=0, column=1, padx=6
        )
        ttk.Label(settings, text="MultiPV").grid(row=0, column=2, sticky="w")
        ttk.Spinbox(settings, from_=1, to=10, textvariable=self.multipv_var, width=8).grid(
            row=0, column=3, padx=6
        )

        ttk.Label(right, textvariable=self.status_var).pack(anchor="w")
        ttk.Label(
            right, textvariable=self.eval_var,
            font=("TkDefaultFont", 11, "bold")
        ).pack(anchor="w", pady=(4, 8))

        ttk.Label(right, text="Engine lines").pack(anchor="w")
        self.lines = tk.Text(right, height=20, width=60, state="disabled")
        self.lines.pack(fill="both", expand=True)

    def _engine(self):
        if self.service is not None:
            return self.service
        if not os.path.isfile(self.engine_path):
            raise FileNotFoundError(
                "Stockfish executable not found: " + self.engine_path
            )
        self.service = EngineService(
            self.engine_path, threads=2, hash_mb=128,
            default_multipv=self.multipv_var.get()
        )
        self.service.start()
        return self.service

    def _load_fen(self):
        try:
            self.board = chess.Board(self.fen_var.get().strip())
        except ValueError as exc:
            messagebox.showerror("Invalid FEN", str(exc))
            return
        self.selected = None
        self._draw_board()
        self.status_var.set("Position loaded")

    def _reset(self):
        self.board = chess.Board()
        self.fen_var.set(self.board.fen())
        self.selected = None
        self._draw_board()
        self.eval_var.set("Evaluation: --")
        self._set_lines("")
        self.status_var.set("Ready")

    def _board_click(self, event):
        file_index = event.x // self.square_size
        row = event.y // self.square_size
        if not (0 <= file_index < 8 and 0 <= row < 8):
            return

        square = chess.square(file_index, 7 - row)
        if self.selected is None:
            if self.board.piece_at(square) and self.board.color_at(square) == self.board.turn:
                self.selected = square
                self._draw_board()
            return

        move = chess.Move(self.selected, square)
        if move not in self.board.legal_moves:
            promotion = chess.Move(self.selected, square, promotion=chess.QUEEN)
            if promotion in self.board.legal_moves:
                move = promotion
            else:
                self.selected = None
                self._draw_board()
                return

        self.board.push(move)
        self.selected = None
        self.fen_var.set(self.board.fen())
        self._draw_board()
        self.status_var.set("Position changed")

    def _analyze(self):
        try:
            result = self._engine().analyze({
                "fen": self.board.fen(),
                "depth": max(1, min(self.depth_var.get(), 30)),
                "multipv": max(1, min(self.multipv_var.get(), 10)),
            })
        except Exception as exc:
            messagebox.showerror("Engine error", str(exc))
            self.status_var.set("Engine error")
            return

        self.status_var.set("Analysis complete")
        lines = result.get("lines", [])
        if lines:
            first = lines[0]
            mate = first.get("mate")
            if mate is not None:
                self.eval_var.set(f"Evaluation: mate {mate:+d}")
            else:
                cp = first.get("score_cp")
                self.eval_var.set(
                    "Evaluation: --" if cp is None else f"Evaluation: {cp / 100:+.2f}"
                )

        output = []
        for index, line in enumerate(lines, start=1):
            san = " ".join(line.get("pv_san", []))
            score = line.get("score_cp")
            mate = line.get("mate")
            if mate is not None:
                score_text = f"M{mate:+d}"
            elif score is None:
                score_text = "--"
            else:
                score_text = f"{score / 100:+.2f}"
            output.append(
                f"{index}. {line.get('bestmove_san', '--')} [{score_text}] "
                f"depth {line.get('depth', '--')}\n   {san}"
            )

        self._set_lines("\n\n".join(output))

    def _set_lines(self, value):
        self.lines.configure(state="normal")
        self.lines.delete("1.0", "end")
        self.lines.insert("1.0", value)
        self.lines.configure(state="disabled")

    def _draw_board(self):
        self.canvas.delete("all")
        for row in range(8):
            for col in range(8):
                square = chess.square(col, 7 - row)
                x1 = col * self.square_size
                y1 = row * self.square_size
                x2 = x1 + self.square_size
                y2 = y1 + self.square_size
                fill = "#f0d9b5" if (row + col) % 2 == 0 else "#b58863"
                if square == self.selected:
                    fill = "#e7c65c"
                self.canvas.create_rectangle(x1, y1, x2, y2, fill=fill, outline="")
                piece = self.board.piece_at(square)
                if piece:
                    self.canvas.create_text(
                        x1 + self.square_size / 2,
                        y1 + self.square_size / 2,
                        text=self._piece_symbol(piece.symbol()),
                        font=("Segoe UI Symbol", 42)
                    )

    @staticmethod
    def _piece_symbol(symbol):
        return {
            "K": "♔", "Q": "♕", "R": "♖", "B": "♗", "N": "♘", "P": "♙",
            "k": "♚", "q": "♛", "r": "♜", "b": "♝", "n": "♞", "p": "♟",
        }[symbol]

    def _close(self):
        if self.service is not None:
            self.service.stop()
        self.root.destroy()


def main():
    parser = argparse.ArgumentParser(description="Cheezie Windows desktop analyzer")
    parser.add_argument(
        "--engine",
        default=os.environ.get("CHEEZIE_STOCKFISH", "stockfish.exe"),
        help="Path to a Windows Stockfish executable"
    )
    args = parser.parse_args()
    root = tk.Tk()
    CheezieWindows(root, args.engine)
    root.mainloop()


if __name__ == "__main__":
    main()
