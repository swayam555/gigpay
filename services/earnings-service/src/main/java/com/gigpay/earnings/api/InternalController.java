package com.gigpay.earnings.api;

import com.gigpay.earnings.trip.EarningsQueries;
import com.gigpay.earnings.worker.Worker;
import com.gigpay.earnings.worker.WorkerRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Service-to-service API (ledger payout destinations, the AI coach's tools). Not routed by the
 * gateway; in Kubernetes a NetworkPolicy restricts who can call it.
 */
@RestController
@RequestMapping("/internal/workers/{id}")
public class InternalController {

    private final WorkerRepository workers;
    private final EarningsQueries queries;

    public InternalController(WorkerRepository workers, EarningsQueries queries) {
        this.workers = workers;
        this.queries = queries;
    }

    @GetMapping
    public ResponseEntity<Worker> worker(@PathVariable String id) {
        return workers.find(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/summary")
    public EarningsQueries.Summary summary(@PathVariable String id, @RequestParam(defaultValue = "today") String period) {
        return queries.summary(id, period);
    }

    @GetMapping("/trips")
    public List<EarningsQueries.Trip> trips(@PathVariable String id, @RequestParam(defaultValue = "10") int limit,
                                            @RequestParam(defaultValue = "recent") String order) {
        return queries.trips(id, Math.min(limit, 50), order);
    }

    @GetMapping("/incentives")
    public List<EarningsQueries.Incentive> incentives(@PathVariable String id) {
        return workers.find(id).map(w -> queries.incentives(id, w.platforms())).orElse(List.of());
    }

    @GetMapping("/daily")
    public List<Map<String, Object>> daily(@PathVariable String id) {
        return queries.daily(id);
    }
}
