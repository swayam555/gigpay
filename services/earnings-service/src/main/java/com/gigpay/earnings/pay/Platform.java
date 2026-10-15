package com.gigpay.earnings.pay;

import java.util.List;

/** A gig platform's pay card. Money in paise. */
public record Platform(String id, String name, String kind, String webhookSecret, int commissionBps, long basePaise,
                       long perKmPaise, long perMinPaise, List<StreakRule> streaks) {

    /** "Complete N trips today, earn a bonus." Thresholds are cumulative, as on most apps. */
    public record StreakRule(int trips, long bonusPaise) {
    }
}
