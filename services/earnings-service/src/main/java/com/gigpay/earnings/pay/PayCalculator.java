package com.gigpay.earnings.pay;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a completed trip into money, line by line, so a worker can see exactly why a trip paid
 * what it paid. The most common complaint from gig workers is not low pay but opaque pay.
 *
 * <ul>
 *   <li><b>Deliveries:</b> base + distance + wait time beyond 5 minutes, times the peak multiplier.
 *       No commission: the platform pays the partner per order.</li>
 *   <li><b>Rides:</b> fare = base + distance + ride time, times surge; the platform keeps a commission.</li>
 *   <li>Tips always go 100% to the worker. Streak bonuses are added on the trip that hits the threshold.</li>
 * </ul>
 */
public final class PayCalculator {

    private PayCalculator() {
    }

    public static Result calculate(Platform p, double distanceKm, int durationMin, int waitMin, double surge,
                                   long tipPaise, int tripsTodayIncludingThis) {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line("Base pay", p.basePaise()));
        long distance = Math.round(distanceKm * p.perKmPaise());
        lines.add(new Line(String.format("Distance (%.1f km × ₹%s)", distanceKm, rupees(p.perKmPaise())), distance));
        long time = 0;
        if ("RIDE".equals(p.kind())) {
            time = (long) durationMin * p.perMinPaise();
            if (time > 0) lines.add(new Line("Ride time (" + durationMin + " min)", time));
        } else if (waitMin > 5 && p.perMinPaise() > 0) {
            time = (long) (waitMin - 5) * p.perMinPaise();
            lines.add(new Line("Restaurant wait beyond 5 min (" + (waitMin - 5) + " min)", time));
        }
        long base = p.basePaise() + distance + time;
        long surgeAmount = Math.round(base * (surge - 1));
        if (surgeAmount > 0) lines.add(new Line(String.format("Peak-hour boost (%.1fx)", surge), surgeAmount));
        long fare = base + surgeAmount;

        long commission = Math.round(fare * p.commissionBps() / 10_000.0);
        if (commission > 0) {
            lines.add(new Line(String.format("%s commission (%.0f%%)", p.name(), p.commissionBps() / 100.0), -commission));
        }
        if (tipPaise > 0) lines.add(new Line("Customer tip (100% yours)", tipPaise));

        long incentive = 0;
        for (Platform.StreakRule rule : p.streaks()) {
            if (rule.trips() == tripsTodayIncludingThis) {
                incentive += rule.bonusPaise();
                lines.add(new Line("Daily streak bonus: " + rule.trips() + " trips", rule.bonusPaise()));
            }
        }
        long net = fare - commission + tipPaise + incentive;
        return new Result(fare, commission, tipPaise, incentive, net, lines);
    }

    static String rupees(long paise) {
        return paise % 100 == 0 ? String.valueOf(paise / 100) : String.format("%.2f", paise / 100.0);
    }

    public record Line(String label, long paise) {
    }

    public record Result(long farePaise, long commissionPaise, long tipPaise, long incentivePaise, long netPaise,
                         List<Line> lines) {
    }
}
