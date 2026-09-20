# Cheezie Termux Engine

Local chess-engine backend for Android + Termux.

## Architecture

```
Browser extension
      |
      | HTTP JSON
      v
Android / Termux
  └─ Cheezie Termux Engine
       └─ Stockfish 19
```

The service runs Stockfish locally and exposes a small API for FEN analysis. It is localhost-only by default.

## Requirements

- Android
- Termux
- Python 3
- ARM64 Android recommended

Stockfish 19 is currently the official stable release. The official release provides Android ARM64-universal and ARMv7-neon binaries. citeturn426201search0turn227268search0

## Install

```bash
pkg update -y
pkg install -y git python curl tar
git clone https://github.com/vhxLUA-max/chessengine-android.git
cd chessengine-android
python -m pip install -r requirements.txt
chmod +x install.sh run.sh
./install.sh
./run.sh
```

The API will listen at:

```
http://127.0.0.1:8765
```

## Health check

Open another Termux session:

```bash
curl http://127.0.0.1:8765/health
```

## Analyze

```bash
curl -X POST http://127.0.0.1:8765/analyze \
  -H 'Content-Type: application/json' \
  -d '{"fen":"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1","depth":12,"multipv":3}'
```

The API returns:

- best move UCI
- best move SAN
- centipawn evaluation
- mate score
- verified UCI PV
- verified SAN PV
- depth
- nodes
- NPS
- search time

PV moves are replayed from the supplied FEN. If a PV ever contains an illegal move, the line stops there. This gives the browser layer a clean legal sequence instead of blindly trusting stale PV data.

## API

### GET /health

Returns service and engine status.

### POST /analyze

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

Use one primary limit: depth, movetime, or nodes. Depth is the default.

### POST /bestmove

Same request format as /analyze, but only the strongest returned line is sent back.

## LAN mode

For a browser running on another device:

```bash
./run.sh --host 0.0.0.0 --port 8765 --token CHANGE_ME
```

Then use:

```
http://PHONE_IP:8765
```

with:

```
Authorization: Bearer CHANGE_ME
```

Do not expose the API directly to the public internet.

## Current scope

This repository is backend-first.

Included:

- Stockfish 19 launcher
- Android ARM64/ARMv7 detection
- HTTP JSON API
- FEN validation
- MultiPV
- depth/time/node search limits
- thread/hash controls
- legal PV verification
- optional LAN bearer authentication
- CORS support

Next layer: connect the Cheezie browser extension to this service and synchronize live board positions.
