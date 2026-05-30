#!/usr/bin/env bash
set -euo pipefail

ASSIGN2_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$ASSIGN2_DIR/.." && pwd)"

cd "$REPO_ROOT"

if [[ ! -d assign2/out ]]; then
  echo "out does not exist. Run ./compile.sh first."
  exit 1
fi

HOST="${1:-localhost}"
PORT="${2:-12345}"

java -cp assign2/out pt.up.fe.cpd.chat.client.ChatClient "$HOST" "$PORT"
