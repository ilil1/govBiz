#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
INFRASTRUCTURE_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${INFRASTRUCTURE_DIR}/compose.yaml"
WAIT_TIMEOUT_SECONDS="${VERIFY_COMPOSE_TIMEOUT_SECONDS:-120}"
WAIT_INTERVAL_SECONDS="${VERIFY_COMPOSE_INTERVAL_SECONDS:-2}"
KEEP_RUNNING="${VERIFY_COMPOSE_KEEP_RUNNING:-false}"
PROJECT_NAME="${VERIFY_COMPOSE_PROJECT_NAME:-govbiz-verify}"

# Verification never uses a developer's real key or the live public API. Exported values take
# precedence over a root .env file for every Compose command executed by this script.
export BIZINFO_API_BASE_URL="http://bizinfo-stub:8001"
export DATA_GO_KR_SERVICE_KEY="compose%2Bverification%2Fkey%3D"
export LLM_PROVIDER="disabled"
export OPENAI_API_KEY=""
export LLM_MODEL_TIMEOUT_SECONDS="2.0"
export LLM_RUN_TIMEOUT_SECONDS="2.5"
export AI_SERVICE_READ_TIMEOUT="3s"

COMPOSE=(
  docker compose
  --profile verification
  --project-name "${PROJECT_NAME}"
  --file "${COMPOSE_FILE}"
)
RESPONSE_DIR="$(mktemp -d)"
LAST_RESPONSE_FILE="${RESPONSE_DIR}/last-response"

cleanup() {
  local exit_code=$?
  trap - EXIT

  if ((exit_code != 0)); then
    echo "Compose verification failed. Current services and logs:" >&2
    "${COMPOSE[@]}" ps >&2 || true
    "${COMPOSE[@]}" logs --no-color >&2 || true
  fi

  if [[ "${KEEP_RUNNING}" != "true" ]]; then
    "${COMPOSE[@]}" down --volumes --remove-orphans >/dev/null 2>&1 || true
  fi

  rm -rf -- "${RESPONSE_DIR}"
  exit "${exit_code}"
}

trap cleanup EXIT

wait_for_http() {
  local label=$1
  local url=$2
  local expected_status=$3
  local expected_body_patterns=("${@:4}")
  local deadline=$((SECONDS + WAIT_TIMEOUT_SECONDS))
  local actual_status="000"
  local body_matches
  local pattern

  while ((SECONDS < deadline)); do
    : >"${LAST_RESPONSE_FILE}"
    actual_status="$(
      curl \
        --silent \
        --output "${LAST_RESPONSE_FILE}" \
        --write-out '%{http_code}' \
        --max-time 5 \
        "${url}" || true
    )"

    if [[ "${actual_status}" == "${expected_status}" ]]; then
      body_matches=true
      for pattern in "${expected_body_patterns[@]}"; do
        if [[ -n "${pattern}" ]] && ! grep -Eq "${pattern}" "${LAST_RESPONSE_FILE}"; then
          body_matches=false
          break
        fi
      done

      if [[ "${body_matches}" == "true" ]]; then
        echo "Verified ${label}: HTTP ${actual_status}"
        return 0
      fi
    fi

    echo "Waiting for ${label}: expected HTTP ${expected_status}, received ${actual_status}"
    sleep "${WAIT_INTERVAL_SECONDS}"
  done

  echo "Timed out waiting for ${label}: expected HTTP ${expected_status}, received ${actual_status}" >&2
  echo "Last response body:" >&2
  sed -n '1,80p' "${LAST_RESPONSE_FILE}" >&2
  return 1
}

wait_for_json_post() {
  local label=$1
  local url=$2
  local request_body=$3
  local expected_status=$4
  local expected_body_pattern=$5
  local deadline=$((SECONDS + WAIT_TIMEOUT_SECONDS))
  local actual_status="000"

  while ((SECONDS < deadline)); do
    : >"${LAST_RESPONSE_FILE}"
    actual_status="$(
      curl \
        --silent \
        --output "${LAST_RESPONSE_FILE}" \
        --write-out '%{http_code}' \
        --max-time 5 \
        --request POST \
        --header 'Accept: application/json' \
        --header 'Content-Type: application/json' \
        --header 'Origin: http://127.0.0.1:5173' \
        --data "${request_body}" \
        "${url}" || true
    )"

    if [[ "${actual_status}" == "${expected_status}" ]] \
        && grep -Eq "${expected_body_pattern}" "${LAST_RESPONSE_FILE}"; then
      echo "Verified ${label}: HTTP ${actual_status}"
      return 0
    fi

    echo "Waiting for ${label}: expected HTTP ${expected_status}, received ${actual_status}"
    sleep "${WAIT_INTERVAL_SECONDS}"
  done

  echo "Timed out waiting for ${label}: expected HTTP ${expected_status}, received ${actual_status}" >&2
  echo "Last response body:" >&2
  sed -n '1,80p' "${LAST_RESPONSE_FILE}" >&2
  return 1
}

wait_for_ai_health_failure() {
  local url=$1
  local deadline=$((SECONDS + WAIT_TIMEOUT_SECONDS))
  local actual_status="000"

  while ((SECONDS < deadline)); do
    : >"${LAST_RESPONSE_FILE}"
    actual_status="$(
      curl \
        --silent \
        --output "${LAST_RESPONSE_FILE}" \
        --write-out '%{http_code}' \
        --max-time 5 \
        "${url}" || true
    )"

    if [[ "${actual_status}" == "503" ]] \
        && grep -Eq '"code"[[:space:]]*:[[:space:]]*"AI_SERVICE_UNAVAILABLE"' "${LAST_RESPONSE_FILE}"; then
      echo "Verified Core to AI Service failure contract: HTTP 503 unavailable"
      return 0
    fi
    if [[ "${actual_status}" == "504" ]] \
        && grep -Eq '"code"[[:space:]]*:[[:space:]]*"AI_SERVICE_TIMEOUT"' "${LAST_RESPONSE_FILE}"; then
      echo "Verified Core to AI Service failure contract: HTTP 504 timeout"
      return 0
    fi

    echo "Waiting for Core to AI Service failure contract: received ${actual_status}"
    sleep "${WAIT_INTERVAL_SECONDS}"
  done

  echo "Timed out waiting for Core to AI Service unavailable/timeout contract" >&2
  echo "Last response body:" >&2
  sed -n '1,80p' "${LAST_RESPONSE_FILE}" >&2
  return 1
}

count_ai_search_intent_requests() {
  local count

  count="$(
    "${COMPOSE[@]}" logs --no-color ai-service 2>/dev/null \
      | grep -Ec '"POST /internal/v1/search-intents/analyze HTTP/[0-9.]+" 200' \
      || true
  )"
  printf '%s' "${count:-0}"
}

wait_for_ai_search_intent_request_after() {
  local baseline_count=$1
  local deadline=$((SECONDS + WAIT_TIMEOUT_SECONDS))
  local current_count

  while ((SECONDS < deadline)); do
    current_count="$(count_ai_search_intent_requests)"
    if [[ "${current_count}" =~ ^[0-9]+$ ]] \
        && ((current_count > baseline_count)); then
      echo "Verified Core to AI search-intent POST: HTTP 200"
      return 0
    fi

    echo "Waiting for Core to AI search-intent POST: expected a new HTTP 200 access log"
    sleep "${WAIT_INTERVAL_SECONDS}"
  done

  echo "Timed out waiting for a new Core to AI search-intent POST access log" >&2
  return 1
}

verify_ai_rule_based_fallback() {
  "${COMPOSE[@]}" exec -T ai-service python -c '
import json
from urllib.request import Request, urlopen

query = "수출 지원사업"
request = Request(
    "http://127.0.0.1:8000/internal/v1/search-intents/analyze",
    data=json.dumps(
        {"query": query, "acceptingOnly": True},
        ensure_ascii=False,
    ).encode("utf-8"),
    headers={"Accept": "application/json", "Content-Type": "application/json"},
    method="POST",
)
with urlopen(request, timeout=5) as response:
    if response.status != 200:
        raise SystemExit("AI fallback endpoint did not return HTTP 200")
    document = json.load(response)

if document.get("originalQuery") != query:
    raise SystemExit("AI fallback response did not echo the query")
if document.get("acceptingOnly") is not True:
    raise SystemExit("AI fallback response changed acceptingOnly")
if document.get("analysisMode") != "RULE_BASED_FALLBACK":
    raise SystemExit("AI Service did not use rule-based fallback")
if document.get("categories") != ["수출"]:
    raise SystemExit("AI fallback response did not extract the expected category")
'
  echo "Verified AI Service disabled-provider contract: RULE_BASED_FALLBACK"
}

echo "Validating Compose configuration"
"${COMPOSE[@]}" config --quiet

echo "Building and starting the GovBiz verification stack (${PROJECT_NAME})"
"${COMPOSE[@]}" up --build --detach --remove-orphans

wait_for_http "Vite web" "http://127.0.0.1:5173/" "200"
wait_for_http "Vite-proxied Core API health" "http://127.0.0.1:5173/api/v1/health" "200" '"status"[[:space:]]*:[[:space:]]*"up".*"service"[[:space:]]*:[[:space:]]*"govbiz-core-api"'
wait_for_http "Vite-proxied Core to AI Service health" "http://127.0.0.1:5173/api/v1/health/ai-service" "200" '"status"[[:space:]]*:[[:space:]]*"up".*"service"[[:space:]]*:[[:space:]]*"govbiz-ai-service"'
AI_SEARCH_INTENT_REQUESTS_BEFORE="$(count_ai_search_intent_requests)"
wait_for_http \
  "Vite-proxied support program search through AI and Bizinfo adapters" \
  "http://127.0.0.1:5173/api/v1/support-programs/search?query=%EC%88%98%EC%B6%9C&acceptingOnly=true" \
  "200" \
  '"query"[[:space:]]*:[[:space:]]*"수출"' \
  '"id"[[:space:]]*:[[:space:]]*"PBLN_COMPOSE_EXPORT"' \
  '"applicationPeriod"[[:space:]]*:[[:space:]]*"2026-08-20 ~ 2099-09-11"' \
  '"status"[[:space:]]*:[[:space:]]*"OPEN"' \
  '"sourceUrl"[[:space:]]*:[[:space:]]*"https://www.bizinfo.go.kr/compose-verification"'
wait_for_ai_search_intent_request_after "${AI_SEARCH_INTENT_REQUESTS_BEFORE}"
verify_ai_rule_based_fallback
wait_for_json_post \
  "Vite-proxied sample item preparation" \
  "http://127.0.0.1:5173/api/v1/sample-items/prepare" \
  '{"item":{"name":"Compose verification item","category":"BASIC","note":"Verifies the reusable sample feature."}}' \
  "200" \
  '"phase"[[:space:]]*:[[:space:]]*"READY_FOR_PROCESSING".*"status"[[:space:]]*:[[:space:]]*"NOT_STARTED"'

echo "Stopping only AI Service to verify failure isolation"
"${COMPOSE[@]}" stop ai-service

wait_for_http "Core API health while AI Service is stopped" "http://127.0.0.1:5173/api/v1/health" "200" '"status"[[:space:]]*:[[:space:]]*"up".*"service"[[:space:]]*:[[:space:]]*"govbiz-core-api"'
wait_for_ai_health_failure "http://127.0.0.1:5173/api/v1/health/ai-service"
wait_for_http \
  "Support program local fallback while AI Service is stopped" \
  "http://127.0.0.1:5173/api/v1/support-programs/search?query=%EC%88%98%EC%B6%9C&acceptingOnly=true" \
  "200" \
  '"query"[[:space:]]*:[[:space:]]*"수출"' \
  '"id"[[:space:]]*:[[:space:]]*"PBLN_COMPOSE_EXPORT"' \
  '"status"[[:space:]]*:[[:space:]]*"OPEN"'

echo "Restarting AI Service to verify recovery without restarting Core API"
"${COMPOSE[@]}" start ai-service

wait_for_http "Core to AI Service recovery" "http://127.0.0.1:5173/api/v1/health/ai-service" "200" '"status"[[:space:]]*:[[:space:]]*"up".*"service"[[:space:]]*:[[:space:]]*"govbiz-ai-service"'

echo "Compose verification passed: AI fallback, search, startup, failure isolation, and recovery are valid."
