# Cheezie Android

A native Android companion for chess analysis with a local Stockfish engine running in Termux.

Cheezie Android is designed to bring the core analysis workflow of the Cheezie project to chess apps outside the browser. The Android side handles screen capture, board positioning, visual overlays, and optional gesture input. Termux provides the local chess-engine backend.

> **Status:** Work in progress. The engine/API foundation is implemented, while live board tracking, app profiles, and several higher-level Cheezie features are still being developed.

## Overview

### Architecture

```text
┌─────────────────────────────┐
│        Android App          │
│                             │
│  MainActivity               │
│  Accessibility Service      │
│  Screen capture             │
│  Board coordinates          │
│  Arrow overlay              │
│  Optional gesture input     │
└──────────────┬──────────────┘
               │ HTTP / JSON
               ▼
┌─────────────────────────────┐
│           Termux            │
│                             │
│  Python HTTP server         │
│  python-chess               │
│  Stockfish                  │
└─────────────────────────────┘
```

The intended flow is:

1. The Android service captures the chess app screen.
2. The service samples the configured 8×8 board area.
3. Board-cell signatures are sent to the local Termux service.
4. The backend tracks the position and requests MultiPV analysis from Stockfish.
5. Verified engine lines are returned to Android.
6. The Android overlay draws the suggested moves.
7. Optional accessibility gestures can play the returned move.

## Features

### Implemented foundation

- Native Android companion app
- Termux-based local engine backend
- Stockfish launcher for supported Android ARM architectures
- Python HTTP/JSON API
- FEN validation through `python-chess`
- Stockfish depth, time, and node limits
- MultiPV analysis
- Configurable engine threads and hash size
- Legal principal-variation verification
- UCI and SAN move output
- Android accessibility overlay
- Configurable board position and size
- White/black board orientation
- Up to five analysis arrows
- Optional automatic move gestures
- Optional bearer-token authentication for non-local connections
- CORS support for compatible clients

### Planned

The project is intended to grow toward feature parity with the Cheezie analysis workflow, including:

- Reliable live board-state tracking
- Chess-app profiles and automatic profile detection
- More robust orientation detection
- Move classification
- Accuracy and estimated Elo
- Evaluation bar
- Coach and hint controls
- Voice/TTS coaching
- Configurable arrow colors and display modes
- Auto-start/game-state detection
- Delay and timing controls
- Safer automatic move handling, including promotion cases
- Import/export of analysis configuration

Features are added incrementally as the Android integration becomes stable.

## Requirements

### Android

- Android 11 (API 30) or newer
- Accessibility Service enabled for screen capture, overlays, and gesture input
- A chess app that can be displayed on the Android screen

### Termux

- Termux
- Python 3
- Git
- cURL
- GNU tar
- ARM64 or ARMv7 Android device for the bundled Stockfish installer

The installer in this repository targets **Stockfish 19** and downloads the matching Android build for the detected CPU architecture.

## Project structure

```text
chessengine-android/
├── app/
│   └── src/main/
│       ├── java/com/vhx/chessengine/
│       │   ├── MainActivity.java
│       │   ├── TermuxClient.java
│       │   ├── OverlayView.java
│       │   ├── OverlayViewV2.java
│       │   ├── ChessAccessibilityService.java
│       │   ├── ChessAccessibilityServiceV2.java
│       │   └── BoardDefaults.java
│       ├── res/
│       └── AndroidManifest.xml
├── .github/workflows/android.yml
├── config.json
├── engine_server.py
├── server.py
├── install.sh
├── run.sh
├── requirements.txt
├── client_example.py
├── settings.gradle.kts
├── build.gradle.kts
└── README.md
```

## Installation

Clone the repository in Termux:

```bash
pkg update -y
pkg install -y git python curl tar

git clone https://github.com/vhxLUA-max/chessengine-android.git
cd chessengine-android
```

Install the Python dependency:

```bash
python -m pip install -r requirements.txt
```

Install the bundled Stockfish binary:

```bash
chmod +x install.sh run.sh
./install.sh
```

The installer detects the device architecture, downloads the matching Stockfish package, extracts the engine, and performs a basic UCI startup check.

## Running the engine API

The engine-only API can be started directly with:

```bash
python engine_server.py
```

By default it listens on:

```text
http://127.0.0.1:8765
```

You can also use the repository launcher:

```bash
./run.sh
```

The launcher is intended to start the integrated service in `server.py`. The live board-detection/session layer is still under active development, so the direct `engine_server.py` path is the reference backend for engine-only testing.

## Health check

From another Termux session:

```bash
curl http://127.0.0.1:8765/health
```

A healthy engine service returns JSON containing the service status and the Stockfish engine state.

## Analyze a position

Example starting-position request:

```bash
curl -X POST http://127.0.0.1:8765/analyze \
  -H 'Content-Type: application/json' \
  -d '{
    "fen":"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
    "depth":12,
    "multipv":3
  }'
```

The analysis response contains:

- Best move in UCI notation
- Best move in SAN notation
- Centipawn evaluation
- Mate score when applicable
- Verified UCI PV
- Verified SAN PV
- Search depth and selective depth
- Nodes searched
- NPS
- Search time

### PV verification

Engine PVs are replayed from the supplied FEN before they are returned.

If a move in a PV is illegal for the current replayed position, the line is truncated at that point instead of returning an invalid sequence. This is an important part of the project because the Android overlay must never draw a continuation based on a stale or illegal engine line.

## API reference

### `GET /health`

Returns service and engine status.

Example:

```json
{
  "ok": true,
  "service": "cheeezie-termux-engine",
  "engine": "stockfish",
  "engine_running": true
}
```

### `POST /analyze`

Request:

```json
{
  "fen": "required",
  "depth": 12,
  "movetime": 1000,
  "nodes": 1000000,
  "multipv": 3,
  "threads": 2,
  "hash": 128
}
```

Use one primary search limit: `depth`, `movetime`, or `nodes`. Depth is used when another limit is not supplied.

### `POST /bestmove`

Uses the same position and search parameters as `/analyze`, but returns the first engine line in a smaller response.

Typical fields:

```json
{
  "ok": true,
  "fen": "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
  "side": "white",
  "bestmove_uci": "e2e4",
  "bestmove_san": "e4",
  "score_cp": 30,
  "mate": null,
  "depth": 12
}
```

## LAN mode

The backend can be bound to a network interface for communication with an Android client on the same network:

```bash
./run.sh --host 0.0.0.0 --port 8765 --token CHANGE_ME
```

The Android client can then use:

```text
http://PHONE_IP:8765
```

with:

```text
Authorization: Bearer CHANGE_ME
```

Do not expose the service directly to the public internet. Use a strong token and restrict access to a trusted local network.

## Android setup

After building and installing the Android app:

1. Open **Cheezie Android**.
2. Set the Termux API URL. For a local Termux service, use `http://127.0.0.1:8765`.
3. Set the board X coordinate, Y coordinate, and board size so the 8×8 sampling area matches the chessboard.
4. Set orientation to `white` or `black`.
5. Save the settings.
6. Enable **Cheezie Android Service** under Android Accessibility settings.
7. Start the Termux engine and use **Test Termux Engine** from the app.
8. Open the target chess app and verify that the overlay is aligned with the board.

### Board coordinates

The current Android service uses a manually configured square board region:

- **Board X:** left edge of the board
- **Board Y:** top edge of the board
- **Board size:** total board width/height
- **Orientation:** `white` or `black`

The live auto-detection and per-app profile system are planned improvements.

## Building the Android app

The project uses Gradle and the Android application plugin.

To build locally from a machine with a working Android/Gradle environment:

```bash
gradle assembleDebug
```

The debug APK is produced at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

A GitHub Actions workflow is also included at:

```text
.github/workflows/android.yml
```

## Configuration

Termux backend defaults are stored in `config.json`.

The Android application stores its connection and board settings in Android `SharedPreferences`.

Typical backend settings include:

```json
{
  "host": "127.0.0.1",
  "port": 8765,
  "engine_path": "engine/stockfish",
  "threads": 2,
  "hash": 128,
  "multipv": 5,
  "token": ""
}
```

Adjust thread count, hash size, and MultiPV to match the available device resources.

## Security and privacy

The project is designed to run locally on the Android device.

- Keep the default bind address at `127.0.0.1` when local-only access is sufficient.
- Use bearer authentication when exposing the service over a LAN.
- Do not publish your access token.
- Do not expose the engine API directly to the public internet.
- Screen capture data is processed as part of the local Android/Termux workflow; the project does not require a cloud engine.

## Development notes

The repository intentionally separates the UI/capture layer from the chess-engine layer.

- **Android:** screen capture, board geometry, overlays, gestures, and user-facing controls.
- **Termux:** Python service, FEN handling, position analysis, and Stockfish process management.
- **python-chess:** legal chess-state validation and PV replay.
- **Stockfish:** local engine calculation.

This separation makes it possible to improve board detection or Android-specific integrations without changing the core engine protocol.

## Known limitations

The current repository snapshot is not feature-complete.

In particular:

- Live board-state tracking is still being developed.
- App-specific profiles are not yet implemented.
- The current Android UI exposes only the core connection and board-position settings.
- Advanced Cheezie features such as accuracy, coaching, move classification, and configuration profiles are not yet implemented in the Android app.
- Promotion-aware automatic move handling still needs a complete implementation.
- Runtime behavior depends on the Android version, chess app, screen layout, and accessibility restrictions.

The README documents the intended architecture and the current repository contents; it does not imply that every planned feature is already production-ready.

## Roadmap

### Phase 1 — Engine foundation
- [x] Local Stockfish backend
- [x] FEN validation
- [x] MultiPV
- [x] Search limits
- [x] PV legality verification
- [x] Android-to-Termux HTTP client foundation

### Phase 2 — Live board integration
- [ ] Reliable piece/position tracking
- [ ] Move synchronization
- [ ] Game reset detection
- [ ] Orientation detection
- [ ] App profiles

### Phase 3 — Cheezie-style analysis
- [ ] Move classification
- [ ] Accuracy calculation
- [ ] Evaluation bar
- [ ] Coach and hint system
- [ ] Configurable arrows
- [ ] Voice/TTS output

### Phase 4 — Automation
- [ ] Auto-start
- [ ] Safe auto-move
- [ ] Promotion handling
- [ ] Delay/timing controls
- [ ] Additional game-state safeguards

## Disclaimer

Cheezie Android is a local analysis and automation project for Android. Use accessibility, screen-capture, and input features only where permitted by the target chess application and applicable terms of service.

## License

No open-source license has been declared for this repository yet. Until a license is added, treat the repository as **all rights reserved** and do not assume permission to redistribute or modify the code outside the rights granted by GitHub or the repository owner.
