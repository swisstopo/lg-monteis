#!/usr/bin/env bash
# MON-196 NFR6 latency baseline.
#
# Measures the response time of the two read endpoints whose authorization path MON-196 changes:
#   GET /api/experiments   and   GET /api/measurements
# It obtains an access token for a seeded dev user through the local Keycloak password grant
# (public client with direct access grants, docker/keycloak/realm/realm-base.json), sends N
# authenticated requests per endpoint and prints the median and p95 in milliseconds.
#
# Prerequisites: the local stack (docker/compose.yml, profile core) and the core backend running,
# plus curl, jq and sort on PATH.
#
# Credentials are never hardcoded; supply them through the environment:
#   MONTEIS_USERNAME   (required) seeded dev user, e.g. one of the users in patch.local.json
#   MONTEIS_PASSWORD   (required) that user's password
# Optional:
#   KC_URL        Keycloak base URL incl. relative path  (default http://localhost:8081/auth)
#   KC_REALM      realm name                              (default monteis)
#   KC_CLIENT_ID  public client with direct access grants (default monteis-spa)
#   API_URL       backend base URL                        (default http://localhost:8080)
#   REQUESTS      requests per endpoint, at least 20      (default 20)
#   PAGE_END_ROW  endRow query parameter for both lists   (default 100)
#
# Usage:
#   MONTEIS_USERNAME=... MONTEIS_PASSWORD=... scripts/mon-196-latency-baseline.sh
set -euo pipefail

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

for tool in curl jq sort; do
  command -v "$tool" > /dev/null 2>&1 || fail "required tool '$tool' is not on PATH"
done

: "${MONTEIS_USERNAME:?MONTEIS_USERNAME must be set (seeded dev user)}"
: "${MONTEIS_PASSWORD:?MONTEIS_PASSWORD must be set}"

KC_URL="${KC_URL:-http://localhost:8081/auth}"
KC_REALM="${KC_REALM:-monteis}"
KC_CLIENT_ID="${KC_CLIENT_ID:-monteis-spa}"
API_URL="${API_URL:-http://localhost:8080}"
REQUESTS="${REQUESTS:-20}"
PAGE_END_ROW="${PAGE_END_ROW:-100}"

[[ "$REQUESTS" =~ ^[0-9]+$ ]] || fail "REQUESTS must be a positive integer, got '$REQUESTS'"
((REQUESTS >= 20)) || fail "REQUESTS must be at least 20 (NFR6), got $REQUESTS"
[[ "$PAGE_END_ROW" =~ ^[0-9]+$ ]] || fail "PAGE_END_ROW must be a non-negative integer"

token_response="$(curl --silent --show-error --fail-with-body \
  --data-urlencode "grant_type=password" \
  --data-urlencode "client_id=${KC_CLIENT_ID}" \
  --data-urlencode "username=${MONTEIS_USERNAME}" \
  --data-urlencode "password=${MONTEIS_PASSWORD}" \
  "${KC_URL}/realms/${KC_REALM}/protocol/openid-connect/token")" \
  || fail "token request to ${KC_URL} failed: ${token_response:-no response}"

ACCESS_TOKEN="$(jq -r '.access_token // empty' <<< "$token_response")"
[[ -n "$ACCESS_TOKEN" ]] || fail "token response contained no access_token"

# Prints "<median_ms> <p95_ms>" for the given URL, using nearest-rank percentiles.
measure() {
  local url="$1" samples=() i code seconds
  for ((i = 1; i <= REQUESTS; i++)); do
    read -r code seconds < <(curl --silent --output /dev/null \
      --write-out '%{http_code} %{time_total}\n' \
      --header "Authorization: Bearer ${ACCESS_TOKEN}" \
      "$url") || fail "request $i to $url failed"
    [[ "$code" == "200" ]] || fail "request $i to $url returned HTTP $code"
    samples+=("$(awk -v s="$seconds" 'BEGIN { printf "%.1f", s * 1000 }')")
  done
  printf '%s\n' "${samples[@]}" | sort -n | awk -v n="$REQUESTS" '
    { v[NR] = $1 }
    END {
      median = (n % 2) ? v[(n + 1) / 2] : (v[n / 2] + v[n / 2 + 1]) / 2
      rank = int(0.95 * n); if (rank < 0.95 * n) rank++
      printf "%.1f %.1f\n", median, v[rank]
    }'
}

query="startRow=0&endRow=${PAGE_END_ROW}"
echo "user=${MONTEIS_USERNAME} requests_per_endpoint=${REQUESTS} api=${API_URL}"
printf '%-24s %12s %12s\n' "endpoint" "median_ms" "p95_ms"
for path in /api/experiments /api/measurements; do
  read -r median p95 < <(measure "${API_URL}${path}?${query}")
  printf '%-24s %12s %12s\n' "GET ${path}" "$median" "$p95"
done
