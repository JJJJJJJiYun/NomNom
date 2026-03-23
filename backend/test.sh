#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
rm -rf out
mkdir -p out
javac -d out $(find src -name '*.java' | sort)
java -cp out com.nomnom.backend.NomNomServer 18080 > /tmp/nomnom-backend.log 2>&1 &
SERVER_PID=$!
cleanup() {
  kill "$SERVER_PID" >/dev/null 2>&1 || true
}
trap cleanup EXIT
for _ in $(seq 1 20); do
  if curl -sf http://127.0.0.1:18080/api/v1/health >/dev/null; then
    break
  fi
  sleep 0.5
done
HEALTH_JSON=$(curl -sf http://127.0.0.1:18080/api/v1/health)
printf '%s' "$HEALTH_JSON" | python -c 'import json,sys; payload=json.load(sys.stdin); assert payload["status"] == "ok", payload'
SEARCH_JSON=$(curl -sf http://127.0.0.1:18080/api/v1/restaurants)
printf '%s' "$SEARCH_JSON" | python -c 'import json,sys; payload=json.load(sys.stdin); assert len(payload["restaurants"]) == 4, payload; assert len(payload["restaurants"][0]["snapshot"]["reasons"]) == 4, payload'
CREATE_JSON=$(curl -sf -X POST http://127.0.0.1:18080/api/v1/decisions -H 'Content-Type: application/json' -d '{"budgetMin":80,"budgetMax":180,"peopleCount":2,"maxDistanceMeters":1500,"preferredCuisines":["日式烧肉","居酒屋","割烹日料"]}')
SESSION_ID=$(printf '%s' "$CREATE_JSON" | python -c 'import json,sys; print(json.load(sys.stdin)["sessionId"])')
MATCHUP_ID=$(printf '%s' "$CREATE_JSON" | python -c 'import json,sys; print(json.load(sys.stdin)["nextMatchup"]["matchupId"])')
for _ in 1 2 3; do
  RESPONSE=$(curl -sf -X POST "http://127.0.0.1:18080/api/v1/decisions/$SESSION_ID/vote" -H 'Content-Type: application/json' -d "{\"matchupId\":\"$MATCHUP_ID\",\"winner\":\"LEFT\"}")
  STATUS=$(printf '%s' "$RESPONSE" | python -c 'import json,sys; print(json.load(sys.stdin)["status"])')
  if [ "$STATUS" = "COMPLETED" ]; then
    printf '%s' "$RESPONSE" | python -c 'import json,sys; p=json.load(sys.stdin); assert p["result"]["winner"]["restaurant"]["name"] == "鸟居烧肉", p; assert len(p["result"]["history"]) == 3, p'
    exit 0
  fi
  MATCHUP_ID=$(printf '%s' "$RESPONSE" | python -c 'import json,sys; print(json.load(sys.stdin)["nextMatchup"]["matchupId"])')
done
printf 'Decision flow did not complete as expected\n' >&2
exit 1
