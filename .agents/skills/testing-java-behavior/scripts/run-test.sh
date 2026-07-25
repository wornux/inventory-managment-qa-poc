#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 ClassName[,OtherClass]" >&2
  exit 2
fi

ROOT="$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel)"
PORT="${PORT:-8080}"

if lsof -nP -iTCP:"$PORT" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "Application detected on port $PORT; running only the requested test class(es)."
fi

"$ROOT/mvnw" -f "$ROOT/pom.xml" -Dtest="$1" test
