package com.gigpay.coach.ai;

import com.gigpay.coach.kb.Passage;
import com.gigpay.coach.tools.WorkerData;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The coach without a language model: a keyword intent router (English and Hinglish) that calls
 * the same data tools and fills templates, plus extractive answers from the knowledge base. Less
 * fluent than an LLM, but always available, free and never makes numbers up.
 */
@Component
public class FallbackCoach {

    private static final Pattern PAYOUT_PROBLEM = Pattern.compile("fail|not received|nahi aaya|nahi mila|pending|stuck|status|where is my");
    private static final Pattern WITHDRAW = Pattern.compile("withdraw|payout|balance|wallet|nikal|cash ?out|how much can i (take|get)|paisa (chahiye|nikalna)");
    private static final Pattern WHY_LESS = Pattern.compile("why .*(less|low|kam|drop)|kam kyu|kyon kam|kyu kam|less than last|lower");
    private static final Pattern LOWEST = Pattern.compile("lowest|sabse kam|least paid|which trip");
    private static final Pattern INCENTIVE = Pattern.compile("incentive|bonus|streak|target");
    private static final Pattern EARNINGS = Pattern.compile("earn|kamaya|kamai|income|made|kitna");
    private static final Pattern WEEK = Pattern.compile("week|hafte|hafta|7 days");
    private static final Set<String> STOP = Set.of("the", "and", "for", "what", "how", "can", "should", "does", "with",
            "this", "that", "are", "you", "your", "about", "from", "have", "who", "when", "which", "will", "there");

    private final WorkerData data;

    public FallbackCoach(WorkerData data) {
        this.data = data;
    }

    public Reply answer(String workerId, String message, List<Passage> passages) {
        String m = message.toLowerCase(Locale.ROOT);
        if (PAYOUT_PROBLEM.matcher(m).find() && (m.contains("pay") || m.contains("paisa") || m.contains("money"))) {
            return payoutStatus(workerId);
        }
        if (WHY_LESS.matcher(m).find()) return whyLess(workerId);
        if (LOWEST.matcher(m).find()) return lowestTrips(workerId);
        if (INCENTIVE.matcher(m).find() && !m.contains("tax")) return incentives(workerId);
        if (WITHDRAW.matcher(m).find()) return wallet(workerId);
        if (EARNINGS.matcher(m).find() && !m.contains("tax")) return earnings(workerId, WEEK.matcher(m).find() ? "week" : "today");
        return knowledge(message, passages);
    }

    private Reply earnings(String workerId, String period) {
        Map<String, Object> s = data.summary(workerId, period);
        long trips = num(s, "trips");
        if (trips == 0) {
            return new Reply("No completed trips " + ("week".equals(period) ? "this week" : "today") + " yet. Once a trip ends it shows up here within seconds.",
                    List.of("earningsSummary"));
        }
        StringBuilder sb = new StringBuilder();
        sb.append("week".equals(period) ? "**This week** " : "**Today** ").append("you earned **")
                .append(rupees(num(s, "netPaise"))).append("** from ").append(trips).append(trips == 1 ? " trip" : " trips");
        long minutes = num(s, "minutesWorked");
        if (minutes > 0) sb.append(" in about ").append(minutes / 60).append(" h ").append(minutes % 60).append(" min (")
                .append(rupees(num(s, "netPerHourPaise"))).append(" per hour)");
        sb.append(".\n\n");
        sb.append("- Trip pay: ").append(rupees(num(s, "farePaise"))).append('\n');
        if (num(s, "incentivePaise") > 0) sb.append("- Bonuses: ").append(rupees(num(s, "incentivePaise"))).append('\n');
        if (num(s, "tipPaise") > 0) sb.append("- Tips: ").append(rupees(num(s, "tipPaise"))).append('\n');
        if (num(s, "commissionPaise") > 0) sb.append("- Platform commission: −").append(rupees(num(s, "commissionPaise"))).append('\n');
        if (s.get("byPlatform") instanceof List<?> byPlatform && byPlatform.size() > 1) {
            sb.append("\nBy platform: ").append(byPlatform.stream().map(o -> (Map<?, ?>) o)
                    .map(p -> p.get("platform") + " " + rupees(((Number) p.get("net")).longValue()))
                    .collect(Collectors.joining(", "))).append('.');
        }
        return new Reply(sb.toString().trim(), List.of("earningsSummary"));
    }

    private Reply whyLess(String workerId) {
        Map<String, Object> now = data.summary(workerId, "week");
        List<Map<String, Object>> days = data.daily(workerId);
        long today = days.isEmpty() ? 0 : num(days.get(days.size() - 1), "net");
        double avgPrev = days.size() <= 1 ? 0 : days.subList(0, days.size() - 1).stream()
                .filter(d -> num(d, "trips") > 0).mapToLong(d -> num(d, "net")).average().orElse(0);
        long todayTrips = days.isEmpty() ? 0 : num(days.get(days.size() - 1), "trips");
        double avgTrips = days.size() <= 1 ? 0 : days.subList(0, days.size() - 1).stream()
                .filter(d -> num(d, "trips") > 0).mapToLong(d -> num(d, "trips")).average().orElse(0);
        StringBuilder sb = new StringBuilder();
        sb.append("Today so far: **").append(rupees(today)).append("** from ").append(todayTrips)
                .append(todayTrips == 1 ? " trip" : " trips").append(". Your recent working-day average is **").append(rupees(Math.round(avgPrev)))
                .append("** from about ").append(Math.round(avgTrips)).append(" trips.\n\n");
        if (avgTrips > 0 && todayTrips < avgTrips * 0.8) {
            sb.append("- The main reason is **fewer trips** (").append(todayTrips).append(" vs ~").append(Math.round(avgTrips))
                    .append("). Lunch (12-2 PM) and dinner (7-10 PM) peaks pay a 1.2-1.5x boost.\n");
        }
        List<Map<String, Object>> incentives = data.incentives(workerId);
        for (Map<String, Object> i : incentives) {
            if (i.get("nextTarget") instanceof Number target) {
                sb.append("- ").append(i.get("platform")).append(": ").append(i.get("tripsToday")).append(" of ")
                        .append(target).append(" trips for the next streak bonus (")
                        .append(rupees(((Number) i.get("nextBonusPaise")).longValue())).append(").\n");
            }
        }
        if (num(now, "commissionPaise") > 0) {
            sb.append("- This week the platform commission was ").append(rupees(num(now, "commissionPaise")))
                    .append(". Tips are never commissioned.\n");
        }
        sb.append("\nAsk me \"which trip paid the least?\" to see a line-by-line breakdown.");
        return new Reply(sb.toString(), List.of("dailyEarnings", "incentiveProgress", "earningsSummary"));
    }

    private Reply lowestTrips(String workerId) {
        List<Map<String, Object>> trips = data.trips(workerId, 3, "lowest");
        if (trips.isEmpty()) return new Reply("No trips in the last 7 days.", List.of("trips"));
        StringBuilder sb = new StringBuilder("Your lowest-paid recent trips:\n\n");
        for (Map<String, Object> t : trips) {
            sb.append("**").append(rupees(num(t, "netPaise"))).append("** · ").append(t.get("platform")).append(", ")
                    .append(t.get("distanceKm")).append(" km, ").append(t.get("pickupArea")).append(" → ").append(t.get("dropArea")).append('\n');
            if (t.get("breakdown") instanceof List<?> lines) {
                for (Object o : lines) {
                    Map<?, ?> l = (Map<?, ?>) o;
                    sb.append("  - ").append(l.get("label")).append(": ").append(signed(((Number) l.get("paise")).longValue())).append('\n');
                }
            }
        }
        sb.append("\nShort trips pay mostly the base amount, so they look small; they are usually quick to finish.");
        return new Reply(sb.toString(), List.of("trips"));
    }

    private Reply incentives(String workerId) {
        List<Map<String, Object>> list = data.incentives(workerId);
        StringBuilder sb = new StringBuilder("Today's streak bonuses:\n\n");
        for (Map<String, Object> i : list) {
            sb.append("- **").append(i.get("platform")).append("**: ").append(i.get("tripsToday")).append(" trips done");
            if (i.get("nextTarget") instanceof Number target) {
                long left = target.longValue() - num(i, "tripsToday");
                sb.append(", ").append(left).append(" more for ").append(rupees(num(i, "nextBonusPaise")));
            } else {
                sb.append(", all of today's bonuses earned 🎉");
            }
            if (num(i, "earnedTodayPaise") > 0) sb.append(" (earned so far: ").append(rupees(num(i, "earnedTodayPaise"))).append(")");
            sb.append('\n');
        }
        return new Reply(sb.toString(), List.of("incentiveProgress"));
    }

    private Reply wallet(String workerId) {
        Map<String, Object> w = data.wallet(workerId);
        long owed = num(w, "owed");
        long now = num(w, "withdrawableNow");
        String text = "Your GigPay balance is **" + rupees(owed) + "**. "
                + (now > 0 ? "You can withdraw up to **" + rupees(now) + "** instantly right now (fee " + rupees(num(w, "instantFee"))
                + "), " + num(w, "payoutsLeftToday") + " instant payouts left today."
                : "Nothing is available for an instant payout right now.")
                + "\n\nThe rest is paid free in your weekly payout. We keep " + (100 - num(w, "withdrawablePercent"))
                + "% back until then in case a platform adjusts a trip.";
        return new Reply(text, List.of("wallet"));
    }

    private Reply payoutStatus(String workerId) {
        List<Map<String, Object>> payouts = data.payouts(workerId);
        if (payouts.isEmpty()) return new Reply("You haven't taken any payouts yet.", List.of("payouts"));
        Map<String, Object> p = payouts.get(0);
        String status = String.valueOf(p.get("status"));
        String text = switch (status) {
            case "SUCCEEDED" -> "Your latest payout of **" + rupees(num(p, "amount")) + "** to " + p.get("upiId")
                    + " **succeeded**. If you can't see it, check that UPI ID's bank account; the bank reference is " + p.get("providerRef") + ".";
            case "FAILED" -> "Your latest payout of **" + rupees(num(p, "amount")) + "** failed (" + p.get("failureReason")
                    + "). The full amount and the fee are **back in your GigPay balance**. Check the UPI ID and try again.";
            default -> "Your latest payout of **" + rupees(num(p, "amount")) + "** is **in progress** (" + status.toLowerCase()
                    + "). If the bank is slow we retry automatically, and if it finally fails the money comes back to your balance.";
        };
        return new Reply(text, List.of("payouts"));
    }

    private Reply knowledge(String question, List<Passage> passages) {
        Set<String> terms = tokens(question);
        record Scored(String text, int ref, double score) {
        }
        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < passages.size(); i++) {
            for (String s : passages.get(i).content().split("(?<=[.!?])\\s+(?=[A-Z(])|\\n+")) {
                String clean = s.replaceFirst("^[-*\\d.\\s]+", "").trim();
                if (clean.length() < 25) continue;
                long overlap = tokens(clean).stream().filter(terms::contains).count();
                if (overlap > 0) scored.add(new Scored(clean, i + 1, overlap + 0.4 * (passages.size() - i)));
            }
        }
        if (scored.isEmpty()) {
            return new Reply("I can help with your earnings, bonuses, balance and payouts, and questions about tax, insurance, "
                    + "e-Shram and safety. Could you rephrase?", List.of());
        }
        String body = scored.stream().sorted(Comparator.comparingDouble(Scored::score).reversed()).limit(4)
                .map(s -> "- " + s.text() + " [" + s.ref() + "]").collect(Collectors.joining("\n"));
        return new Reply(body, List.of());
    }

    private static Set<String> tokens(String text) {
        return Pattern.compile("[^a-z0-9₹]+").splitAsStream(text.toLowerCase(Locale.ROOT))
                .filter(w -> w.length() > 2 && !STOP.contains(w))
                .map(w -> w.endsWith("s") && w.length() > 4 ? w.substring(0, w.length() - 1) : w)
                .collect(Collectors.toSet());
    }

    static long num(Map<?, ?> m, String key) {
        Object v = m.get(key);
        return v instanceof Number n ? Math.round(n.doubleValue()) : 0;
    }

    static String rupees(long paise) {
        // NumberFormat is mutable and not thread-safe: one per call.
        NumberFormat f = NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));
        int digits = paise % 100 == 0 ? 0 : 2;
        f.setMinimumFractionDigits(digits);
        f.setMaximumFractionDigits(digits);
        return f.format(paise / 100.0);
    }

    static String signed(long paise) {
        return paise < 0 ? "−" + rupees(-paise) : rupees(paise);
    }

    public record Reply(String text, List<String> toolsUsed) {
    }
}
