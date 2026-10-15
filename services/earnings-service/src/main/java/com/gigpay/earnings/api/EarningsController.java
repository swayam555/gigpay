package com.gigpay.earnings.api;

import com.gigpay.earnings.trip.EarningsQueries;
import com.gigpay.earnings.worker.Worker;
import com.gigpay.earnings.worker.WorkerRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/** Worker-facing earnings API. The worker id comes from the gateway-verified token. */
@RestController
@RequestMapping("/api/earnings")
public class EarningsController {

    private final EarningsQueries queries;
    private final WorkerRepository workers;

    public EarningsController(EarningsQueries queries, WorkerRepository workers) {
        this.queries = queries;
        this.workers = workers;
    }

    @GetMapping("/me")
    public Worker me(@RequestHeader("X-User-Id") String workerId) {
        return workers.find(workerId).orElseThrow(() -> new NoSuchElementException("Worker not found"));
    }

    @GetMapping("/summary")
    public EarningsQueries.Summary summary(@RequestHeader("X-User-Id") String workerId,
                                           @RequestParam(defaultValue = "today") String period) {
        return queries.summary(workerId, period);
    }

    @GetMapping("/daily")
    public List<Map<String, Object>> daily(@RequestHeader("X-User-Id") String workerId) {
        return queries.daily(workerId);
    }

    @GetMapping("/trips")
    public List<EarningsQueries.Trip> trips(@RequestHeader("X-User-Id") String workerId,
                                            @RequestParam(defaultValue = "30") int limit,
                                            @RequestParam(defaultValue = "recent") String order) {
        return queries.trips(workerId, Math.min(limit, 200), order);
    }

    @GetMapping("/incentives")
    public List<EarningsQueries.Incentive> incentives(@RequestHeader("X-User-Id") String workerId) {
        return queries.incentives(workerId, me(workerId).platforms());
    }
}
