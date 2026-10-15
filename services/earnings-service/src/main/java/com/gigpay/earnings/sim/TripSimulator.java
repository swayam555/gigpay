package com.gigpay.earnings.sim;

import com.gigpay.earnings.config.JsonCodec;
import com.gigpay.earnings.pay.Platform;
import com.gigpay.earnings.pay.PlatformRepository;
import com.gigpay.earnings.trip.Hmac;
import com.gigpay.earnings.trip.TripReport;
import com.gigpay.earnings.worker.Worker;
import com.gigpay.earnings.worker.WorkerRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Plays the partner platforms: sends signed "trip completed" webhooks over HTTP, exactly as
 * FoodRunner, RideNow and QuickDrop would. It can back-fill the past six days (so charts and
 * balances look like a real week) and then stream live trips, with lunch and dinner peaks.
 */
@Component
public class TripSimulator {

    private static final Logger log = LoggerFactory.getLogger(TripSimulator.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    // Pickups and drops stay inside the worker's own city.
    private static final Map<String, String[]> AREAS = Map.of(
            "Bengaluru", new String[]{"Koramangala", "Indiranagar", "HSR Layout", "Whitefield", "Jayanagar", "BTM Layout", "Marathahalli"},
            "Mumbai", new String[]{"Andheri", "Bandra", "Powai", "Dadar", "Lower Parel", "Goregaon", "Chembur"},
            "Delhi", new String[]{"Saket", "Connaught Place", "Lajpat Nagar", "Hauz Khas", "Karol Bagh", "Dwarka", "Rohini"},
            "Hyderabad", new String[]{"Gachibowli", "Hitech City", "Madhapur", "Banjara Hills", "Kondapur", "Begumpet", "Ameerpet"},
            "Pune", new String[]{"Kothrud", "Baner", "Hinjewadi", "Viman Nagar", "Koregaon Park", "Aundh", "Wakad"});

    private final RestClient self;
    private final PlatformRepository platforms;
    private final WorkerRepository workers;
    private final JsonCodec json;
    private final JdbcClient jdbc;
    private final Random rnd = new Random();
    private final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor();
    private static final Duration MIN_GAP = Duration.ofMinutes(2);

    private final AtomicLong sent = new AtomicLong();
    private final Map<String, Instant> lastTrip = new ConcurrentHashMap<>();
    private volatile ScheduledFuture<?> loop;
    private volatile double perMinute = 15;
    private volatile boolean backfilling;

    public TripSimulator(RestClient.Builder builder, @Value("${server.port:8101}") int port, PlatformRepository platforms,
                         WorkerRepository workers, JsonCodec json, JdbcClient jdbc) {
        JdkClientHttpRequestFactory rf = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(2)).build());
        this.self = builder.baseUrl("http://localhost:" + port).requestFactory(rf).build();
        this.platforms = platforms;
        this.workers = workers;
        this.json = json;
        this.jdbc = jdbc;
    }

    public synchronized Status start(double tripsPerMinute) {
        this.perMinute = Math.max(1, Math.min(tripsPerMinute, 600));
        if (loop == null || loop.isDone()) {
            if (jdbc.sql("SELECT count(*) FROM trips").query(Long.class).single() == 0) {
                Executors.newVirtualThreadPerTaskExecutor().submit(this::backfill);
            }
            loop = ticker.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
        }
        return status();
    }

    public synchronized Status stop() {
        if (loop != null) loop.cancel(false);
        loop = null;
        return status();
    }

    public Status status() {
        return new Status(loop != null && !loop.isDone(), backfilling, perMinute, sent.get());
    }

    private double carry;

    private void tick() {
        if (backfilling) return;
        carry += perMinute / 60.0;
        int n = (int) carry;
        carry -= n;
        List<Worker> all = workers.all();
        Instant now = Instant.now();
        for (int i = 0; i < n; i++) {
            // The demo worker gets a steady share so their dashboard fills while you watch.
            Worker w = rnd.nextDouble() < 0.12 ? all.get(0) : all.get(rnd.nextInt(all.size()));
            // Nobody finishes a trip every few seconds: skip workers who completed one recently.
            Instant last = lastTrip.get(w.id());
            if (last != null && last.plus(MIN_GAP).isAfter(now)) continue;
            lastTrip.put(w.id(), now);
            send(w, now);
        }
    }

    /** Six days of history: each worker does 8-20 trips a day across their shift. */
    void backfill() {
        backfilling = true;
        long start = System.currentTimeMillis();
        try {
            ZonedDateTime today = ZonedDateTime.now(IST).toLocalDate().atStartOfDay(IST);
            for (Worker w : workers.all()) {
                for (int day = 6; day >= 1; day--) {
                    if (rnd.nextDouble() < 0.15) continue; // a day off
                    int trips = 8 + rnd.nextInt(13);
                    List<Instant> times = new ArrayList<>();
                    for (int t = 0; t < trips; t++) {
                        int minute = 9 * 60 + rnd.nextInt(13 * 60); // 9 AM to 10 PM
                        times.add(today.minusDays(day).plusMinutes(minute).toInstant());
                    }
                    times.sort(null);
                    times.forEach(t -> send(w, t));
                }
            }
            log.info("Back-filled six days of trips in {} ms", System.currentTimeMillis() - start);
        } finally {
            backfilling = false;
        }
    }

    private void send(Worker w, Instant completedAt) {
        Platform p = platforms.find(w.platforms().get(rnd.nextInt(w.platforms().size()))).orElseThrow();
        boolean ride = "RIDE".equals(p.kind());
        double km = Math.round((ride ? 2 + rnd.nextDouble() * 9 : 1 + rnd.nextDouble() * 5.5) * 10) / 10.0;
        int duration = (int) Math.round(km * (ride ? 3.2 : 3.8) + rnd.nextInt(6));
        int wait = ride ? 0 : rnd.nextInt(14);
        int hour = completedAt.atZone(IST).getHour();
        boolean peak = (hour >= 12 && hour < 14) || (hour >= 19 && hour < 22);
        double surge = peak ? 1.2 + rnd.nextInt(4) * 0.1 : (rnd.nextDouble() < 0.1 ? 1.1 : 1.0);
        long tip = rnd.nextDouble() < 0.18 ? (1 + rnd.nextInt(5)) * 1000L : 0;
        String[] areas = AREAS.getOrDefault(w.city(), AREAS.get("Bengaluru"));
        int from = rnd.nextInt(areas.length);
        int to = (from + 1 + rnd.nextInt(areas.length - 1)) % areas.length;
        TripReport r = new TripReport(UUID.randomUUID().toString().substring(0, 12), w.id(), km, duration, wait,
                Math.round(surge * 10) / 10.0, tip, areas[from], areas[to],
                completedAt);
        String body = json.write(r);
        String ts = String.valueOf(System.currentTimeMillis() / 1000);
        try {
            self.post().uri("/webhooks/platforms/{p}/trips", p.id())
                    .header("X-GigPay-Timestamp", ts)
                    .header("X-GigPay-Signature", Hmac.sign(p.webhookSecret(), ts, body))
                    .header("Content-Type", "application/json")
                    .body(body).retrieve().toBodilessEntity();
            sent.incrementAndGet();
        } catch (RuntimeException e) {
            log.debug("Webhook failed: {}", e.getMessage());
        }
    }

    @PreDestroy
    void shutdown() {
        ticker.shutdownNow();
    }

    public record Status(boolean running, boolean backfilling, double tripsPerMinute, long tripsSent) {
    }
}
