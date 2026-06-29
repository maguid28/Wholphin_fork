#!/usr/bin/env bash
# Fetch recent Jellyfin server logs and filter for delete-related errors.
#
# Usage:
#   JELLYFIN_URL=http://your-server:8096 JELLYFIN_TOKEN=your_access_token ./scripts/fetch-jellyfin-logs.sh
#
# Or pass as arguments:
#   ./scripts/fetch-jellyfin-logs.sh http://your-server:8096 your_access_token

set -euo pipefail

JELLYFIN_URL="${1:-${JELLYFIN_URL:-}}"
JELLYFIN_TOKEN="${2:-${JELLYFIN_TOKEN:-}}"

if [[ -z "$JELLYFIN_URL" || -z "$JELLYFIN_TOKEN" ]]; then
  echo "Usage: JELLYFIN_URL=... JELLYFIN_TOKEN=... $0" >&2
  echo "   or: $0 <server_url> <access_token>" >&2
  exit 1
fi

JELLYFIN_URL="${JELLYFIN_URL%/}"
AUTH_HEADER="MediaBrowser Client=\"NdorfinLogFetch\", Device=\"Script\", DeviceId=\"log-fetch\", Version=\"1.0.0\", Token=\"${JELLYFIN_TOKEN}\""

echo "Fetching log file list from ${JELLYFIN_URL} ..."
LOG_FILES=$(curl -sS -H "Authorization: ${AUTH_HEADER}" "${JELLYFIN_URL}/System/Logs")
LATEST_NAME=$(python3 - <<'PY' "$LOG_FILES"
import json, sys
logs = json.load(sys.stdin)
if not logs:
    raise SystemExit("No log files returned (admin token required)")
logs.sort(key=lambda x: x.get("DateModified") or x.get("DateCreated") or "", reverse=True)
print(logs[0]["Name"])
PY
)

echo "Latest log file: ${LATEST_NAME}"
OUT_FILE="jellyfin-log-${LATEST_NAME}"
curl -sS -H "Authorization: ${AUTH_HEADER}" \
  "${JELLYFIN_URL}/System/Logs/Log?name=$(python3 -c "import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1]))" "${LATEST_NAME}")" \
  -o "${OUT_FILE}"

echo "Saved full log to ${OUT_FILE}"
echo
echo "=== Delete-related lines ==="
grep -iE 'DELETE|DeleteItem|delete item|Error processing request.*DELETE|UnauthorizedAccess|FOREIGN KEY|Permission denied' "${OUT_FILE}" | tail -40 || echo "(no delete-related lines found in tail filter)"
