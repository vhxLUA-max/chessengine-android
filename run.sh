#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"

if [ ! -x "$ROOT/engine/stockfish" ]; then
  echo "Stockfish is not installed."
  echo "Run: ./install.sh"
  exit 1
fi

exec python "$ROOT/server.py" "$@"
