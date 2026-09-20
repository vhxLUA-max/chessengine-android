#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
ENGINE_DIR="$ROOT/engine"
TMP_DIR="$ROOT/.download"

mkdir -p "$ENGINE_DIR" "$TMP_DIR"

ARCH="$(uname -m)"

case "$ARCH" in
  aarch64|arm64)
    ASSET="stockfish-android-arm64-universal.tar.gz"
    ;;
  armv7l|armv8l)
    ASSET="stockfish-android-armv7-neon.tar.gz"
    ;;
  *)
    echo "Unsupported CPU architecture: $ARCH"
    echo "Supported: Android ARM64 or ARMv7."
    exit 1
    ;;
esac

URL="https://sourceforge.net/projects/stockfish.mirror/files/sf_19/"$ASSET"/download"
ARCHIVE="$TMP_DIR/"$ASSET

echo "[1/4] Downloading Stockfish 19..."
curl -L --fail --retry 3 "$URL" -o "$ARCHIVE"

echo "[2/4] Extracting..."
rm -rf "$TMP_DIR/extracted"
mkdir -p "$TMP_DIR/extracted"
tar -xzf "$ARCHIVE" -C "$TMP_DIR/extracted"

BIN="$(find "$TMP_DIR/extracted" -type f -name 'stockfish-*' | head -n 1 || true)"

if [ -z "$BIN" ]; then
  echo "Could not find the Stockfish binary."
  find "$TMP_DIR/extracted" -maxdepth 3 -type f -print
  exit 1
fi

echo "[3/4] Installing..."
cp "$BIN" "$ENGINE_DIR/stockfish"
chmod +x "$ENGINE_DIR/stockfish"

echo "[4/4] Checking UCI..."
"$ENGINE_DIR/stockfish" <<'EOF'
uci
quit
EOF

echo
echo "Stockfish 19 installed successfully."
echo "Run: ./run.sh"
