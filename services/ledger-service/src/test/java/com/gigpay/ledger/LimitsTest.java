package com.gigpay.ledger;

import com.gigpay.ledger.config.LedgerProperties;
import com.gigpay.ledger.payout.Limits;
import com.gigpay.ledger.payout.PayoutRepository.TodayUsage;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LimitsTest {

    static final LedgerProperties P = new LedgerProperties(0.9, 10_000, 500_000, 3, 500, "u", "s", 0,
            Duration.ofSeconds(1), Duration.ofSeconds(1));
    static final TodayUsage NONE = new TodayUsage(0, 0);

    @Test
    void ninetyPercentOfEarningsMinusFeeRoundedDownToRupees() {
        // ₹1,234.56 earned → 90% = ₹1,111.10 → minus ₹5 fee = ₹1,106.10 → ₹1,106
        assertThat(Limits.withdrawable(123_456, NONE, P)).isEqualTo(110_600);
    }

    @Test
    void cappedByTheDailyLimit() {
        assertThat(Limits.withdrawable(10_000_000, new TodayUsage(1, 450_000), P)).isEqualTo(50_000);
    }

    @Test
    void nothingBelowTheMinimumOrAfterThreePayouts() {
        assertThat(Limits.withdrawable(10_000, NONE, P)).isZero();
        assertThat(Limits.withdrawable(10_000_000, new TodayUsage(3, 30_000), P)).isZero();
    }

    @Test
    void explainsWhyAPayoutIsRefused() {
        assertThatThrownBy(() -> Limits.check(5_000, 1_000_000, NONE, P)).hasMessageContaining("Minimum");
        assertThatThrownBy(() -> Limits.check(10_050, 1_000_000, NONE, P)).hasMessageContaining("whole rupees");
        assertThatThrownBy(() -> Limits.check(90_000, 50_000, NONE, P)).hasMessageContaining("up to ₹");
    }
}
