#!/usr/bin/env bash
set -euo pipefail

ASSIGN2_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$ASSIGN2_DIR/.." && pwd)"

cd "$REPO_ROOT"

if [[ ! -d assign2/out ]]; then
  echo "out does not exist. Run ./compile.sh first."
  exit 1
fi

PORT="${1:-12345}"

java -cp assign2/out pt.up.fe.cpd.chat.server.ChatServer "$PORT"
