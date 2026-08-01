#!/usr/bin/env bash

set -Eeuo pipefail
set +x

PROJECT_ROOT="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

REPORT_DIRECTORY="${PROJECT_ROOT}/zap/reports"

KEYCLOAK_REALM="${KEYCLOAK_REALM:-wornux}"
ZAP_CLIENT_ID="${ZAP_CLIENT_ID:-inventory-automation}"

APP_BASE_URL="${APP_BASE_URL:-http://localhost:8080}"
KEYCLOAK_BASE_URL="${KEYCLOAK_BASE_URL:-http://localhost:7777}"

# Address used from inside the ZAP container.
# When the application runs in Docker Compose, this can be http://app:8080.
ZAP_TARGET_URL="${ZAP_TARGET_URL:-http://app:8080}"

ZAP_ENVIRONMENT="${ZAP_ENVIRONMENT:-local}"
ZAP_IMAGE="${ZAP_IMAGE:-ghcr.io/zaproxy/zaproxy:stable@sha256:8d387b1a63e3425beef4846e39719f5af2a787753af2d8b6558c6257d7a577a2}"
ZAP_STARTUP_TIMEOUT_MINUTES="${ZAP_STARTUP_TIMEOUT_MINUTES:-10}"
ZAP_ENV_FILE=""

: "${KEYCLOAK_AUTOMATION_CLIENT_SECRET:?Set KEYCLOAK_AUTOMATION_CLIENT_SECRET}"

case "${ZAP_ENVIRONMENT}" in
  local | staging)
    ;;
  production)
    echo "Active ZAP scans must not run against production." >&2
    exit 2
    ;;
  *)
    echo "ZAP_ENVIRONMENT must be local or staging." >&2
    exit 2
    ;;
esac

cleanup() {
  if [[ -n "${ZAP_ENV_FILE}" ]]; then
    rm -f "${ZAP_ENV_FILE}"
  fi

  unset ACCESS_TOKEN
  unset ZAP_ACCESS_TOKEN
}

trap cleanup EXIT

TOKEN_ENDPOINT="${KEYCLOAK_BASE_URL%/}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token"

echo "Obtaining Keycloak service-account token..."

TOKEN_RESPONSE="$(
  curl \
    --fail \
    --silent \
    --show-error \
    --request POST \
    "${TOKEN_ENDPOINT}" \
    --header "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "grant_type=client_credentials" \
    --data-urlencode "client_id=${ZAP_CLIENT_ID}" \
    --data-urlencode "client_secret=${KEYCLOAK_AUTOMATION_CLIENT_SECRET}"
)"

ACCESS_TOKEN="$(
  TOKEN_RESPONSE="${TOKEN_RESPONSE}" python3 - <<'PY'
import json
import os

response = json.loads(os.environ["TOKEN_RESPONSE"])
token = response.get("access_token")

if not isinstance(token, str) or not token.strip():
    raise SystemExit("Keycloak did not return a valid access_token.")

print(token)
PY
)"

EXPIRES_IN="$(
  TOKEN_RESPONSE="${TOKEN_RESPONSE}" python3 - <<'PY'
import json
import os

response = json.loads(os.environ["TOKEN_RESPONSE"])
expires_in = response.get("expires_in")

if not isinstance(expires_in, int):
    raise SystemExit("Keycloak did not return a numeric expires_in.")

print(expires_in)
PY
)"

if (( EXPIRES_IN < 1200 || EXPIRES_IN > 2100 )); then
  echo \
    "The automation token expires in ${EXPIRES_IN}s. Expected approximately 1800s." \
    >&2
  exit 1
fi

echo "Token obtained with a lifetime of ${EXPIRES_IN} seconds."

echo "Checking unauthenticated access..."

UNAUTHENTICATED_STATUS="$(
  curl \
    --silent \
    --show-error \
    --output /dev/null \
    --write-out "%{http_code}" \
    "${APP_BASE_URL%/}/api/products"
)"

if [[ "${UNAUTHENTICATED_STATUS}" != "401" ]]; then
  echo \
    "Expected 401 without a token, received ${UNAUTHENTICATED_STATUS}." \
    >&2
  exit 1
fi

echo "Validating effective Inventory Viewer permissions..."

PERMISSIONS_RESPONSE="$(
  curl \
    --fail \
    --silent \
    --show-error \
    --header "Authorization: Bearer ${ACCESS_TOKEN}" \
    "${APP_BASE_URL%/}/api/me/permissions"
)"

PERMISSIONS_RESPONSE="${PERMISSIONS_RESPONSE}" python3 - <<'PY'
import json
import os

response = json.loads(os.environ["PERMISSIONS_RESPONSE"])

expected = {
    "product:view",
    "category:view",
    "supplier:view",
    "stock-movement:view",
    "report:view",
}

permissions = response.get("data", {}).get("permissions")

if not isinstance(permissions, list):
    raise SystemExit("The permissions endpoint did not return a permissions list.")

actual = set(permissions)

if actual != expected:
    raise SystemExit(
        "Unexpected automation permissions.\n"
        f"Expected: {sorted(expected)}\n"
        f"Actual:   {sorted(actual)}"
    )
PY

echo "Checking that write operations remain forbidden..."

FORBIDDEN_STATUS="$(
  curl \
    --silent \
    --show-error \
    --output /dev/null \
    --write-out "%{http_code}" \
    --request DELETE \
    --header "Authorization: Bearer ${ACCESS_TOKEN}" \
    "${APP_BASE_URL%/}/api/products/9223372036854775807"
)"

if [[ "${FORBIDDEN_STATUS}" != "403" ]]; then
  echo \
    "Expected 403 for an Inventory Viewer write operation, received ${FORBIDDEN_STATUS}." \
    >&2
  exit 1
fi

echo "Checking the OpenAPI definition..."

curl \
  --fail \
  --silent \
  --show-error \
  --output /dev/null \
  "${APP_BASE_URL%/}/v3/api-docs"

mkdir -p "${REPORT_DIRECTORY}"
find "${REPORT_DIRECTORY}" -mindepth 1 ! -name ".gitkeep" -delete

ZAP_OPENAPI_URL="${ZAP_TARGET_URL%/}/v3/api-docs"

ZAP_AUTH_HEADER_SITE="$(
  ZAP_TARGET_URL="${ZAP_TARGET_URL}" python3 - <<'PY'
import os
from urllib.parse import urlparse

target = os.environ["ZAP_TARGET_URL"]
hostname = urlparse(target).hostname

if not hostname:
    raise SystemExit(f"Could not determine target hostname from {target!r}.")

print(hostname)
PY
)"

ZAP_ENV_FILE="$(mktemp)"
chmod 600 "${ZAP_ENV_FILE}"
{
  printf '%s\n' "ZAP_AUTH_HEADER=Authorization"
  printf '%s\n' "ZAP_AUTH_HEADER_VALUE=Bearer ${ACCESS_TOKEN}"
  printf '%s\n' "ZAP_AUTH_HEADER_SITE=${ZAP_AUTH_HEADER_SITE}"
} > "${ZAP_ENV_FILE}"

run_zap_container() {
  docker run \
    --rm \
    "$@" \
    --env-file "${ZAP_ENV_FILE}" \
    --volume "${PROJECT_ROOT}/zap:/zap/wrk:rw" \
    "${ZAP_IMAGE}" \
    zap-api-scan.py \
      -t "${ZAP_OPENAPI_URL}" \
      -f openapi \
      -T "${ZAP_STARTUP_TIMEOUT_MINUTES}" \
      -r reports/zap-report.html \
      -J reports/zap-report.json \
      -w reports/zap-report.md
}

echo "Starting authenticated OWASP ZAP API scan..."
echo "Target: ${ZAP_OPENAPI_URL}"
echo "Authentication identity: ${ZAP_CLIENT_ID}"

set +e

if [[ -n "${ZAP_DOCKER_NETWORK:-}" ]]; then
  run_zap_container --network "${ZAP_DOCKER_NETWORK}"
else
  run_zap_container
fi

ZAP_EXIT_CODE=$?

set -e

echo "Sanitizing security reports..."

if ! ZAP_ACCESS_TOKEN="${ACCESS_TOKEN}" \
  python3 "${PROJECT_ROOT}/zap/sanitize-reports.py" "${REPORT_DIRECTORY}"; then
  find "${REPORT_DIRECTORY}" -mindepth 1 ! -name ".gitkeep" -delete
  echo "Report sanitization failed; generated reports were deleted." >&2
  exit 1
fi

if [[ "${ZAP_EXIT_CODE}" -ne 0 ]]; then
  echo "OWASP ZAP finished with exit code ${ZAP_EXIT_CODE}." >&2
  exit "${ZAP_EXIT_CODE}"
fi

echo "OWASP ZAP authenticated API scan completed successfully."
