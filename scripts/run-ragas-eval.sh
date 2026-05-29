#!/usr/bin/env bash
set -euo pipefail

API_URL="${API_URL:-http://127.0.0.1:8081}"
DATASET="${DATASET:-regression}"
TARGET_SERVICE="${TARGET_SERVICE:-MdQnAService}"
RAGAS_GATE_MODE="${RAGAS_GATE_MODE:-warn}"
POLL_SECONDS="${POLL_SECONDS:-10}"

if [[ -z "${ADMIN_TOKEN:-}" ]]; then
  echo "ADMIN_TOKEN is required" >&2
  exit 2
fi

RUN_ID="$(
  curl -fsS -X POST "$API_URL/api/v1/admin/eval-runs" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"type\":\"RAGAS\",\"dataset\":\"$DATASET\",\"targetService\":\"$TARGET_SERVICE\"}" \
    | jq -r '.data.id'
)"

if [[ -z "$RUN_ID" || "$RUN_ID" == "null" ]]; then
  echo "Failed to create Ragas eval run" >&2
  exit 1
fi

while true; do
  DETAIL="$(curl -fsS "$API_URL/api/v1/admin/eval-runs/$RUN_ID" \
    -H "Authorization: Bearer $ADMIN_TOKEN")"
  STATE="$(echo "$DETAIL" | jq -r '.data.run.status')"
  if [[ "$STATE" == "SUCCEEDED" || "$STATE" == "FAILED" ]]; then
    break
  fi
  sleep "$POLL_SECONDS"
done

SUMMARY="$(echo "$DETAIL" | jq -c '.data.run.summaryJson | fromjson? // .data.run.summaryJson')"
echo "$SUMMARY"

if [[ "$RAGAS_GATE_MODE" == "hard" || "$RAGAS_GATE_MODE" == "soft" ]]; then
  echo "$SUMMARY" | jq -e '.thresholdViolations | to_entries | all(.value == 0)' >/dev/null
fi
