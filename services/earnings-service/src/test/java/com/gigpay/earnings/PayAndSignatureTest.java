package com.gigpay.earnings;

import com.gigpay.earnings.pay.PayCalculator;
import com.gigpay.earnings.pay.Platform;
import com.gigpay.earnings.trip.Hmac;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PayAndSignatureTest {

    static final Platform FOOD = new Platform("FOODRUNNER", "FoodRunner", "DELIVERY", "s", 0, 2500, 600, 100,
            List.of(new Platform.StreakRule(10, 10_000), new Platform.StreakRule(15, 15_000)));
    static final Platform RIDE = new Platform("RIDENOW", "RideNow", "RIDE", "s", 1800, 2000, 900, 100, List.of());

    @Test
    void deliveryPaysBaseDistanceAndWaitBeyondFiveMinutes() {
        PayCalculator.Result r = PayCalculator.calculate(FOOD, 3.0, 15, 9, 1.0, 0, 1);
        // ₹25 base + 3 km × ₹6 + 4 min wait × ₹1 = ₹47
        assertThat(r.netPaise()).isEqualTo(4_700);
        assertThat(r.commissionPaise()).isZero();
        assertThat(r.lines()).extracting(PayCalculator.Line::label).contains("Restaurant wait beyond 5 min (4 min)");
    }

    @Test
    void rideTakesCommissionOnTheFareButNeverOnTips() {
        PayCalculator.Result r = PayCalculator.calculate(RIDE, 5.0, 20, 0, 1.0, 3_000, 1);
        // fare ₹20 + 5 × ₹9 + 20 × ₹1 = ₹85; commission 18% = ₹15.30; tip ₹30 untouched
        assertThat(r.farePaise()).isEqualTo(8_500);
        assertThat(r.commissionPaise()).isEqualTo(1_530);
        assertThat(r.netPaise()).isEqualTo(8_500 - 1_530 + 3_000);
    }

    @Test
    void peakBoostMultipliesTheFare() {
        PayCalculator.Result r = PayCalculator.calculate(FOOD, 2.0, 10, 0, 1.5, 0, 1);
        assertThat(r.netPaise()).isEqualTo(Math.round((2_500 + 1_200) * 1.5));
    }

    @Test
    void streakBonusLandsOnTheTripThatHitsTheTarget() {
        assertThat(PayCalculator.calculate(FOOD, 2, 10, 0, 1, 0, 9).incentivePaise()).isZero();
        assertThat(PayCalculator.calculate(FOOD, 2, 10, 0, 1, 0, 10).incentivePaise()).isEqualTo(10_000);
        assertThat(PayCalculator.calculate(FOOD, 2, 10, 0, 1, 0, 11).incentivePaise()).isZero();
        assertThat(PayCalculator.calculate(FOOD, 2, 10, 0, 1, 0, 15).incentivePaise()).isEqualTo(15_000);
    }

    @Test
    void breakdownLinesAddUpToTheNetPay() {
        PayCalculator.Result r = PayCalculator.calculate(RIDE, 7.3, 26, 0, 1.3, 2_000, 1);
        assertThat(r.lines().stream().mapToLong(PayCalculator.Line::paise).sum()).isEqualTo(r.netPaise());
    }

    @Test
    void webhookSignaturesRejectTamperingAndReplays() {
        String now = String.valueOf(System.currentTimeMillis() / 1000);
        String body = "{\"externalId\":\"t1\",\"tipPaise\":0}";
        String sig = Hmac.sign("secret", now, body);
        assertThat(Hmac.verify("secret", now, body, sig)).isTrue();
        assertThat(Hmac.verify("secret", now, body.replace("0}", "99999}"), sig)).isFalse();
        assertThat(Hmac.verify("other-secret", now, body, sig)).isFalse();
        String old = String.valueOf(System.currentTimeMillis() / 1000 - 3600);
        assertThat(Hmac.verify("secret", old, body, Hmac.sign("secret", old, body))).isFalse();
        assertThat(Hmac.verify("secret", null, body, sig)).isFalse();
    }
}
