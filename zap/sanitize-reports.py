from __future__ import annotations

import os
import sys
from pathlib import Path


REDACTED_TOKEN = b"<redacted-access-token>"
REDACTED_BEARER = b"Bearer <redacted-access-token>"


def sanitize_file(path: Path, token: bytes) -> None:
    content = path.read_bytes()

    sanitized = content.replace(b"Bearer " + token, REDACTED_BEARER)
    sanitized = sanitized.replace(token, REDACTED_TOKEN)

    if sanitized != content:
        path.write_bytes(sanitized)

    remaining = path.read_bytes()

    if token in remaining:
        raise RuntimeError(f"Access token remains in report: {path}")


def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: sanitize-reports.py <report-directory>", file=sys.stderr)
        return 2

    token_value = os.environ.get("ZAP_ACCESS_TOKEN", "").strip()

    if not token_value:
        print("ZAP_ACCESS_TOKEN is required.", file=sys.stderr)
        return 2

    report_directory = Path(sys.argv[1])

    if not report_directory.is_dir():
        print(f"Report directory does not exist: {report_directory}", file=sys.stderr)
        return 2

    token = token_value.encode("utf-8")

    for path in report_directory.rglob("*"):
        if path.is_file() and path.name != ".gitkeep":
            sanitize_file(path, token)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
