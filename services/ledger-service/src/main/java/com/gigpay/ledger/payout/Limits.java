package com.gigpay.ledger.payout;

import com.gigpay.ledger.config.LedgerProperties;

/** Earned-wage-access rules, as pure functions so every edge case is unit-tested. */
public final class Limits {

    private Limits() {
    }

    /**
     * The most a worker can receive in one instant payout right now: their share of earned money
     * (the rest is a buffer against platform clawbacks), minus the fee, capped by the daily limit,
     * rounded down to whole rupees.
     */
    public static long withdrawable(long owed, PayoutRepository.TodayUsage today, LedgerProperties p) {
        if (today.count() >= p.dailyInstantCount()) return 0;
        long share = (long) Math.floor(owed * p.withdrawableShare());
        long afterFee = share - p.instantFee();
        long dailyLeft = p.dailyInstantLimit() - today.total();
        long max = Math.min(afterFee, dailyLeft);
        max = max - (max % 100);
        return max >= p.minPayout() ? max : 0;
    }

    public static void check(long amount, long owed, PayoutRepository.TodayUsage today, LedgerProperties p) {
        if (amount < p.minPayout()) {
            throw new LimitExceededException("Minimum instant payout is ₹" + p.minPayout() / 100);
        }
        if (amount % 100 != 0) throw new LimitExceededException("Withdraw whole rupees only");
        if (today.count() >= p.dailyInstantCount()) {
            throw new LimitExceededException("You've used all " + p.dailyInstantCount() + " instant payouts for today");
        }
        long max = withdrawable(owed, today, p);
        if (amount > max) {
            throw new LimitExceededException(max == 0
                    ? "Nothing available to withdraw right now"
                    : "You can withdraw up to ₹" + max / 100 + " right now");
        }
    }

    public static class LimitExceededException extends RuntimeException {
        public LimitExceededException(String message) {
            super(message);
        }
    }
}
