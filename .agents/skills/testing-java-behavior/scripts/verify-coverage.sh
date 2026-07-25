#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)"
PORT="${PORT:-8080}"

if lsof -nP -iTCP:"$PORT" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "Refusing broad Maven verification: an application/debugger is listening on port $PORT." >&2
  echo "Use run-test.sh ClassName for focused feedback, or rerun after the user-approved JVM stop." >&2
  exit 3
fi

"$ROOT/mvnw" -f "$ROOT/pom.xml" verify
python3 "$SCRIPT_DIR/jacoco-report.py" "$ROOT/target/site/jacoco/jacoco.xml" --fail-on-missed
