#!/usr/bin/env bash
set -euo pipefail

ASSIGN2_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

cd "$ASSIGN2_DIR"

mkdir -p out
javac --release 21 -d out $(find src -name "*.java")

echo "Compiled assign2 into out"
