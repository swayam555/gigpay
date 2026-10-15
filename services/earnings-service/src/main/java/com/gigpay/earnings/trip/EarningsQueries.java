package com.gigpay.earnings.trip;

import com.gigpay.earnings.config.JsonCodec;
import com.gigpay.earnings.pay.PayCalculator;
import com.gigpay.earnings.pay.Platform;
import com.gigpay.earnings.pay.PlatformRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read side: everything a worker (or the AI coach, on their behalf) wants to know about their pay. */
@Component
public class EarningsQueries {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final TypeReference<List<PayCalculator.Line>> LINES = new TypeReference<>() {
    };

    private final JdbcClient jdbc;
    private final JsonCodec json;
    private final PlatformRepository platforms;

    public EarningsQueries(JdbcClient jdbc, JsonCodec json, PlatformRepository platforms) {
        this.jdbc = jdbc;
        this.json = json;
        this.platforms = platforms;
    }

    /** today, week (since Monday), lastweek */
    public Summary summary(String workerId, String period) {
        LocalDate today = LocalDate.now(IST);
        LocalDate from;
        LocalDate to;
        switch (period == null ? "today" : period) {
            case "week" -> { from = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); to = today.plusDays(1); }
            case "lastweek" -> {
                to = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                from = to.minusDays(7);
            }
            default -> { from = today; to = today.plusDays(1); }
        }
        Map<String, Object> t = jdbc.sql("""
                        SELECT count(*) AS trips, coalesce(sum(net_paise),0) AS net, coalesce(sum(fare_paise),0) AS fare,
                               coalesce(sum(incentive_paise),0) AS incentives, coalesce(sum(tip_paise),0) AS tips,
                               coalesce(sum(commission_paise),0) AS commission, coalesce(sum(duration_min + wait_min),0) AS minutes,
                               coalesce(sum(distance_km),0) AS km
                        FROM trips WHERE worker_id = :w
                          AND completed_at >= (CAST(:from AS date) AT TIME ZONE 'Asia/Kolkata')
                          AND completed_at <  (CAST(:to AS date) AT TIME ZONE 'Asia/Kolkata')""")
                .param("w", workerId).param("from", from).param("to", to).query().singleRow();
        List<Map<String, Object>> byPlatform = jdbc.sql("""
                        SELECT p.name AS platform, count(*) AS trips, sum(net_paise) AS net, sum(incentive_paise) AS incentives
                        FROM trips t JOIN platforms p ON p.id = t.platform_id WHERE worker_id = :w
                          AND completed_at >= (CAST(:from AS date) AT TIME ZONE 'Asia/Kolkata')
                          AND completed_at <  (CAST(:to AS date) AT TIME ZONE 'Asia/Kolkata')
                        GROUP BY p.name ORDER BY net DESC""")
                .param("w", workerId).param("from", from).param("to", to).query().listOfRows();
        long minutes = ((Number) t.get("minutes")).longValue();
        long net = ((Number) t.get("net")).longValue();
        return new Summary(period, from, to.minusDays(1), ((Number) t.get("trips")).longValue(), net,
                ((Number) t.get("fare")).longValue(), ((Number) t.get("incentives")).longValue(),
                ((Number) t.get("tips")).longValue(), ((Number) t.get("commission")).longValue(), minutes,
                ((Number) t.get("km")).doubleValue(), minutes == 0 ? 0 : Math.round(net * 60.0 / minutes), byPlatform);
    }

    /** Net earnings per day for the last 7 days, for the chart. */
    public List<Map<String, Object>> daily(String workerId) {
        return jdbc.sql("""
                        SELECT to_char(d, 'YYYY-MM-DD') AS day,
                               coalesce((SELECT sum(net_paise) FROM trips t WHERE t.worker_id = :w
                                   AND (t.completed_at AT TIME ZONE 'Asia/Kolkata')::date = d::date), 0) AS net,
                               coalesce((SELECT count(*) FROM trips t WHERE t.worker_id = :w
                                   AND (t.completed_at AT TIME ZONE 'Asia/Kolkata')::date = d::date), 0) AS trips
                        FROM generate_series((now() AT TIME ZONE 'Asia/Kolkata')::date - 6,
                                             (now() AT TIME ZONE 'Asia/Kolkata')::date, interval '1 day') d
                        ORDER BY d""").param("w", workerId).query().listOfRows();
    }

    public List<Trip> trips(String workerId, int limit, String order) {
        String orderBy = "lowest".equals(order) ? "net_paise ASC" : "completed_at DESC";
        return jdbc.sql("""
                        SELECT t.id, p.name AS platform, t.kind, t.distance_km, t.duration_min, t.wait_min, t.surge,
                               t.fare_paise, t.incentive_paise, t.commission_paise, t.tip_paise, t.net_paise, t.breakdown,
                               t.pickup_area, t.drop_area, t.completed_at
                        FROM trips t JOIN platforms p ON p.id = t.platform_id
                        WHERE t.worker_id = ? AND t.completed_at > now() - interval '7 days'
                        ORDER BY\s""" + orderBy + " LIMIT ?")
                .params(workerId, limit)
                .query((rs, n) -> new Trip(rs.getString("id"), rs.getString("platform"), rs.getString("kind"),
                        rs.getDouble("distance_km"), rs.getInt("duration_min"), rs.getInt("wait_min"), rs.getDouble("surge"),
                        rs.getLong("fare_paise"), rs.getLong("incentive_paise"), rs.getLong("commission_paise"),
                        rs.getLong("tip_paise"), rs.getLong("net_paise"), json.read(rs.getString("breakdown"), LINES),
                        rs.getString("pickup_area"), rs.getString("drop_area"), rs.getObject("completed_at", OffsetDateTime.class)))
                .list();
    }

    /** Today's progress toward each platform's streak bonuses. */
    public List<Incentive> incentives(String workerId, List<String> platformIds) {
        List<Incentive> out = new ArrayList<>();
        for (String pid : platformIds) {
            Platform p = platforms.find(pid).orElse(null);
            if (p == null) continue;
            int done = jdbc.sql("""
                            SELECT count(*) FROM trips WHERE worker_id = ? AND platform_id = ?
                              AND (completed_at AT TIME ZONE 'Asia/Kolkata')::date = (now() AT TIME ZONE 'Asia/Kolkata')::date""")
                    .params(workerId, pid).query(Integer.class).single();
            Platform.StreakRule next = p.streaks().stream().filter(s -> s.trips() > done).findFirst().orElse(null);
            long earned = p.streaks().stream().filter(s -> s.trips() <= done).mapToLong(Platform.StreakRule::bonusPaise).sum();
            out.add(new Incentive(p.name(), done, next == null ? null : next.trips(), next == null ? null : next.bonusPaise(), earned));
        }
        return out;
    }

    public Map<String, Object> platformOverview() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lastHour", jdbc.sql("""
                        SELECT p.name AS platform, count(*) AS trips, coalesce(sum(net_paise),0) AS net,
                               count(DISTINCT worker_id) AS workers
                        FROM trips t JOIN platforms p ON p.id = t.platform_id
                        WHERE received_at > now() - interval '1 hour' GROUP BY p.name ORDER BY p.name""").query().listOfRows());
        body.put("tripsTotal", jdbc.sql("SELECT count(*) FROM trips").query(Long.class).single());
        return body;
    }

    public record Summary(String period, LocalDate from, LocalDate to, long trips, long netPaise, long farePaise,
                          long incentivePaise, long tipPaise, long commissionPaise, long minutesWorked, double km,
                          long netPerHourPaise, List<Map<String, Object>> byPlatform) {
    }

    public record Trip(String id, String platform, String kind, double distanceKm, int durationMin, int waitMin,
                       double surge, long farePaise, long incentivePaise, long commissionPaise, long tipPaise,
                       long netPaise, List<PayCalculator.Line> breakdown, String pickupArea, String dropArea,
                       OffsetDateTime completedAt) {
    }

    public record Incentive(String platform, int tripsToday, Integer nextTarget, Long nextBonusPaise, long earnedTodayPaise) {
    }
}
