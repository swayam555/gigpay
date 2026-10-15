package com.gigpay.earnings.api;

import com.gigpay.earnings.pay.Platform;
import com.gigpay.earnings.pay.PlatformRepository;
import com.gigpay.earnings.sim.TripSimulator;
import com.gigpay.earnings.trip.EarningsQueries;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/earnings/admin")
public class AdminController {

    private final TripSimulator simulator;
    private final EarningsQueries queries;
    private final PlatformRepository platforms;

    public AdminController(TripSimulator simulator, EarningsQueries queries, PlatformRepository platforms) {
        this.simulator = simulator;
        this.queries = queries;
        this.platforms = platforms;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        Map<String, Object> body = new java.util.LinkedHashMap<>(queries.platformOverview());
        body.put("simulator", simulator.status());
        return body;
    }

    @GetMapping("/platforms")
    public List<Map<String, Object>> platforms() {
        return platforms.all().stream().map((Platform p) -> Map.<String, Object>of("id", p.id(), "name", p.name(),
                "kind", p.kind(), "commissionPercent", p.commissionBps() / 100.0, "streaks", p.streaks())).toList();
    }

    @PostMapping("/simulator/start")
    public TripSimulator.Status start(@RequestBody(required = false) Map<String, Object> body) {
        double rate = body != null && body.get("tripsPerMinute") instanceof Number n ? n.doubleValue() : 15;
        return simulator.start(rate);
    }

    @PostMapping("/simulator/stop")
    public TripSimulator.Status stop() {
        return simulator.stop();
    }
}
