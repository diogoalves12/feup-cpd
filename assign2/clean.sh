#!/usr/bin/env bash
set -euo pipefail

ASSIGN2_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

cd "$ASSIGN2_DIR"

rm -rf out

echo "Removed out"
