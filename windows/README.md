# Cheezie Windows

The Windows client reuses the repository's Python chess/engine layer instead of creating a second chess implementation.

## Current Windows features

- Native Tkinter desktop UI
- Local Stockfish integration
- FEN loading
- Interactive legal board moves
- Promotion to queen when made from the board UI
- Configurable search depth
- MultiPV analysis
- Evaluation display
- Verified UCI/SAN principal variations from the shared engine service

## Requirements

- Windows 10/11
- Python 3
- python-chess
- A Windows Stockfish executable

Install the Python dependency from the repository root:

    py -m pip install -r requirements.txt

Start the client:

    py windows\cheezie_windows.py --engine C:\path\to\stockfish.exe

You can also set CHEEZIE_STOCKFISH and run without --engine.

## Architecture

Android
  -> Android capture / overlay
  -> Python engine API

Windows
  -> Tkinter desktop UI
  -> Python engine layer
  -> Stockfish

The Windows client deliberately does not modify the Android capture, overlay, accessibility, or automation code.

## Next Windows parity layer

Screen capture, automatic board-geometry detection, transparent analysis overlay, and Windows-specific input automation should be added as separate modules. They should consume the existing board-tracking and engine APIs rather than duplicating chess-state logic.
