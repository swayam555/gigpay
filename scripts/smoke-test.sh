#!/usr/bin/env bash
# End-to-end smoke test against a running stack:  ./scripts/smoke-test.sh [base-url]
# Needs curl, jq and uuidgen. Starts the trip simulator if it isn't running. Takes about a minute.
set -uo pipefail
B=${1:-http://localhost:3003}
FAIL=0
j() { curl -s -H 'Content-Type: application/json' "$@"; }
check() { if [ "$2" = "$3" ]; then echo "  ok   $1"; else echo "  FAIL $1 (expected $3, got $2)"; FAIL=1; fi; }

echo "Sign-in and access control"
W=$(j $B/api/auth/worker -d '{"phone":"9000000001","pin":"1234"}' | jq -r .accessToken)
A=$(j $B/api/auth/admin -d '{"username":"finance","password":"gigpay"}' | jq -r .accessToken)
WH="Authorization: Bearer $W"; AH="Authorization: Bearer $A"
check "wrong PIN is rejected" "$(j -o /dev/null -w '%{http_code}' $B/api/auth/worker -d '{"phone":"9000000001","pin":"0000"}')" 401
check "no token is rejected" "$(curl -s -o /dev/null -w '%{http_code}' $B/api/wallet)" 401
check "worker can't open the finance API" "$(curl -s -o /dev/null -w '%{http_code}' $B/api/ledger/admin/overview -H "$WH")" 403
check "spoofed X-User-Id header is ignored" "$(curl -s $B/api/earnings/me -H "$WH" -H 'X-User-Id: GW-0002' | jq -r .id)" GW-0001

echo "Trips (simulated platforms)"
j $B/api/earnings/admin/simulator/start -H "$AH" -d '{"tripsPerMinute":15}' >/dev/null
for _ in $(seq 60); do
  s=$(curl -s $B/api/earnings/admin/overview -H "$AH")
  [ "$(echo "$s" | jq .simulator.backfilling)" = false ] && [ "$(echo "$s" | jq .tripsTotal)" -gt 500 ] && break
  sleep 2
done
sleep 5
check "back-fill stored trips" "$([ "$(echo "$s" | jq .tripsTotal)" -gt 500 ] && echo yes)" yes
check "7 days of earnings for the chart" "$(curl -s $B/api/earnings/daily -H "$WH" | jq length)" 7

echo "Instant payout"
# Each worker gets 3 instant payouts a day, and a weekly payout empties every balance, so use the
# first demo worker who has a payout left and has earned at least the ₹100 minimum (waiting up to
# two minutes for new trips if needed).
j $B/api/earnings/admin/simulator/start -H "$AH" -d '{"tripsPerMinute":60}' >/dev/null
found=""
for _ in $(seq 24); do
  for phone in 9000000001 9000000002 9177777868 9549008934 9232931336 9242995371 9832294821; do
    PW=$(j $B/api/auth/worker -d "{\"phone\":\"$phone\",\"pin\":\"1234\"}" | jq -r .accessToken)
    wallet=$(curl -s $B/api/wallet -H "Authorization: Bearer $PW")
    [ "$(echo "$wallet" | jq .withdrawableNow)" -ge 10000 ] && { found=$phone; break 2; }
  done
  sleep 5
done
j $B/api/earnings/admin/simulator/start -H "$AH" -d '{"tripsPerMinute":15}' >/dev/null
PH="Authorization: Bearer $PW"
UPI=$(curl -s $B/api/earnings/me -H "$PH" | jq -r .upiId)
check "money is available to withdraw" "$([ -n "$found" ] && echo yes)" yes
K=$(uuidgen)
P1=$(j $B/api/wallet/withdrawals -H "$PH" -H "Idempotency-Key: $K" -d "{\"amountPaise\":10000,\"upiId\":\"$UPI\"}")
P2=$(j $B/api/wallet/withdrawals -H "$PH" -H "Idempotency-Key: $K" -d "{\"amountPaise\":10000,\"upiId\":\"$UPI\"}")
check "same Idempotency-Key returns the same payout" "$(echo "$P2" | jq -r .id)" "$(echo "$P1" | jq -r .id)"
check "over-limit withdrawal is refused" "$(j -o /dev/null -w '%{http_code}' $B/api/wallet/withdrawals -H "$PH" -d "{\"amountPaise\":99999900,\"upiId\":\"$UPI\"}")" 422
sleep 1
check "invalid UPI ID is refused" "$(j -o /dev/null -w '%{http_code}' $B/api/wallet/withdrawals -H "$PH" -d '{"amountPaise":10000,"upiId":"nope"}')" 400
for _ in $(seq 30); do
  st=$(curl -s $B/api/wallet/payouts/$(echo "$P1" | jq -r .id) -H "$PH" | jq -r .status)
  case $st in SUCCEEDED|FAILED) break;; esac; sleep 1
done
check "payout reaches a final state" "$(case $st in SUCCEEDED|FAILED) echo yes;; esac)" yes

echo "Money coach"
r=$(j $B/api/coach/chat -H "$WH" -d '{"message":"How much did I earn today?"}')
check "reads the worker's own earnings" "$(echo "$r" | jq -r '.toolsUsed | index("earningsSummary") != null')" true
sleep 1.5
r=$(j $B/api/coach/chat -H "$WH" -d '{"message":"How do I register on e-Shram?"}')
check "answers policy questions with citations" "$(echo "$r" | jq '.citations | length > 0')" true

echo "Books"
check "trial balance is zero" "$(curl -s $B/api/ledger/admin/overview -H "$AH" | jq .trialBalance.balanced)" true
check "reconciliation finds no issues" "$(j -X POST $B/api/ledger/admin/reconciliation/run -H "$AH" | jq '.issues | length')" 0

echo "Rate limiting"
sleep 4
codes=""; for _ in 1 2 3 4 5 6; do codes="$codes $(j -o /dev/null -w '%{http_code}' $B/api/wallet/withdrawals -H "$WH" -d '{"amountPaise":10000,"upiId":"x"}')"; done
check "a burst of withdrawals gets HTTP 429" "$(echo "$codes" | grep -q 429 && echo yes)" yes

[ $FAIL = 0 ] && echo "All checks passed." || { echo "Some checks failed."; exit 1; }
