# Testing GigPay

There are four levels: automated tests (no browser), a one-command API smoke test against the running
stack, Playwright browser tests of whole user journeys, and a manual click-through for demos and design reviews.

## 1. Automated tests

```bash
./mvnw verify          # needs Docker for Testcontainers
```

| Test | What it proves |
|---|---|
| `PayAndSignatureTest` (6) | Pay breakdowns for deliveries and rides (wait time, surge, commission, tips, streak bonuses); breakdown lines add up to net pay; webhook signatures reject tampering and replays |
| `LimitsTest` (4) | 90% rule, fee, daily amount and count limits, whole rupees, minimum payout |
| `LedgerIT` (8, real Postgres) | Unbalanced entries are rejected with nothing written; posting twice is a no-op; **20 concurrent withdrawals can't spend the same money twice**; same idempotency key, same payout; a failed payout is reversed and the money comes back; a bank failure is retried under a new reference and late callbacks about the old one are ignored; a successful payout leaves the float exactly once; **a random storm of accruals, withdrawals, failures and settlements leaves the books balanced** |

## 2. Smoke test (stack running)

```bash
docker compose up -d --build
./scripts/smoke-test.sh                 # or: ./scripts/smoke-test.sh http://some-host
```

It prints `ok` or `FAIL` for each of 16 checks: sign-in and roles, spoofed headers, back-fill, the
7-day chart, a withdrawal to completion, idempotency, limits, UPI validation, the coach's tools and
citations, the trial balance, reconciliation and rate limiting.

## 3. Browser tests (Playwright)

```bash
docker compose up -d --build
cd e2e
npm install
npx playwright install chromium          # or set CHROME_PATH=/usr/bin/google-chrome to use an installed Chrome
npm test                                  # BASE_URL defaults to http://localhost:3003
npm run report                            # HTML report, with a trace and screenshot for any failure
```

`tests/journey.spec.ts` drives the 23-step worker and finance journey: landing page, staff sign-in (including a wrong password), simulated platforms, worker sign-in, trips, below-minimum and invalid-UPI withdrawals, a real instant payout to its UTR, the coach, role checks, then the finance console with the bank broken on purpose, reconciliation and the trial balance. Each step is a named `test.step`, so the report shows exactly where
something broke. A second test opens every page at 390 px and fails if any of them scrolls sideways.
Uncaught page errors and any 5xx response fail the run.

The suite can be re-run against the same stack. Three fixes from the final verification pass (2026-10-15)
made that possible:

- If the smoke test has left the trip simulator running, the journey no longer waits for a **Start trips** button.
- Each worker gets only 3 instant payouts a day. The journey now picks the first worker with a payout left from
  the same seven-worker pool the smoke test uses, instead of only the two demo accounts. Before this, the third
  run of the day hung on the wallet page.
- The "break the bank" step waited for the fault panel to load. It now sets only the four failure-rate sliders,
  not the settlement-delay slider. Before this, a fast run found no sliders and passed without injecting any
  faults, and a slow run failed on the delay slider.

## 4. Manual test script

Open two browser windows: the **landing page** at <http://localhost:3003> and the **finance console** at
<http://localhost:3003/finance>.

### A. Start the world

| Step | Do | You should see |
|---|---|---|
| 1 | Finance console → sign in with `finance` / `gigpay` | KPI tiles, "Books balance" in green |
| 2 | Press **Start trips** (top bar) | "Back-filling 6 days of trips…", then "Live · 15 trips/min" with a pulsing dot. The trip count climbs to ~3,000. |
| 3 | Wait 30 s and look at the tiles | "Owed to workers" and "Platforms owe us" are a few lakh rupees; the sidebar still says "Books balance · Σ = 0" |

### B. The landing page and the partner app

| Step | Do | You should see |
|---|---|---|
| 4 | Landing page: watch the hero, then scroll | The phone receives trips and completes a payout on a loop; sections fade in; the ledger keeps posting entries while "Σ all balances" stays ₹0.00 |
| 5 | **Open the app** → pick **Ravi Kumar** → **Sign in** | Ravi's overview: the green balance card, today / per hour / this week / tips, a 7-day area chart |
| 6 | Sign out (top right), try PIN `0000` | "Wrong mobile number or PIN" |
| 7 | **Trips** → **Lowest paid** → open a trip | Its pay breakdown: base, distance, wait time, peak boost, commission (RideNow only), tip |
| 8 | **Overview** → streak bonuses | Progress bars like "8 / 10 trips · ₹100" |

### C. Get paid now

| Step | Do | You should see |
|---|---|---|
| 9 | **Wallet** → amount `50` → **Send ₹50 instantly** | "Minimum instant payout is ₹100" |
| 10 | Change the UPI ID to `not-a-upi` and send ₹100 | "Enter a valid UPI ID" |
| 11 | Restore the UPI ID, tap **50%** → **Send** | "Latest payout": Requested ✓ → Sent to the bank (spinner) → In your account ✓ with a UTR, in about 3-5 s |
| 12 | **Payout history** / **Statement** | The payout marked "Paid"; a statement line for the amount + ₹5 fee |
| 13 | Tap **Max** → **Send** twice quickly | The button switches to "Sending…" after the first tap; one payout is created |
| 14 | Keep withdrawing | After 3 instant payouts today: "You've used all 3 instant payouts for today" |

### D. The coach

| Step | Do | You should see |
|---|---|---|
| 15 | **Coach** → tap "Why are my earnings less today?" | A comparison with your recent average, bonus progress, and "Read your data: dailyEarnings, incentiveProgress, earningsSummary" |
| 16 | Ask "Which trip paid the least?" | Your three lowest trips with breakdowns |
| 17 | Ask "How do I register on e-Shram?" | A cited answer; expand a source to read the passage |
| 18 | Ask "What is the capital of France?" | A polite "I can help with your earnings…" (it stays on topic) |

### E. Break the bank (chaos test)

| Step | Do | You should see |
|---|---|---|
| 19 | Finance console → **Bank fault injection** → set each slider to about 40% → **Apply** | "Applied" |
| 20 | Press **Run weekly payout** | "Weekly payouts started"; on **Overview**, the "Payouts, last hour" counts change |
| 21 | **Payouts** | Some payouts with several attempts (many succeed on a later one); those that fail 4 times show a reason such as "the bank network timed out". Within about a minute, nothing is left "Sent to bank". |
| 22 | **Reconciliation** → **Run now** | 0 issues; "Every bank transfer matches a payout in the ledger, nothing was paid twice…" |
| 23 | Sidebar | Still "Books balance · Σ = 0" |
| 24 | **Journal** | "payout reversed" entries returning money (and the fee) to workers |
| 25 | **Bank fault injection** → **Reset to defaults** | The sliders return to 5% / 5% / 5% / 2% |

### F. Security spot checks (terminal)

```bash
# A worker token can't reach finance endpoints
TOKEN=$(curl -s localhost:3003/api/auth/worker -H 'Content-Type: application/json' \
  -d '{"phone":"9000000001","pin":"1234"}' | jq -r .accessToken)
curl -i localhost:3003/api/ledger/admin/overview -H "Authorization: Bearer $TOKEN"     # 403

# Pretending to be another worker doesn't work
curl -s localhost:3003/api/earnings/me -H "Authorization: Bearer $TOKEN" -H 'X-User-Id: GW-0002' | jq .id   # "GW-0001"

# Unsigned trip webhooks are rejected (earnings-service isn't exposed by the gateway;
# run this inside the Docker network)
docker compose exec earnings-service curl -s -o /dev/null -w '%{http_code}\n' \
  -X POST localhost:8101/webhooks/platforms/FOODRUNNER/trips -H 'Content-Type: application/json' -d '{}'   # 401
```

## Resetting

```bash
docker compose down -v      # wipes the database; the next "Start trips" back-fills again
```
