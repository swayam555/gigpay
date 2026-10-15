package com.gigpay.earnings.trip;

import com.gigpay.contracts.EarningAccrued;
import com.gigpay.contracts.Topics;
import com.gigpay.earnings.config.JsonCodec;
import com.gigpay.earnings.config.Outbox;
import com.gigpay.earnings.pay.PayCalculator;
import com.gigpay.earnings.pay.Platform;
import com.gigpay.earnings.worker.WorkerRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.ZoneId;
import java.util.UUID;

@Service
public class TripService {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final JdbcClient jdbc;
    private final WorkerRepository workers;
    private final Outbox outbox;
    private final TransactionTemplate tx;
    private final JsonCodec json;
    private final MeterRegistry meters;

    public TripService(JdbcClient jdbc, WorkerRepository workers, Outbox outbox, TransactionTemplate tx, JsonCodec json,
                       MeterRegistry meters) {
        this.jdbc = jdbc;
        this.workers = workers;
        this.outbox = outbox;
        this.tx = tx;
        this.json = json;
        this.meters = meters;
    }

    /**
     * Records a completed trip exactly once and, in the same transaction, queues the earning for
     * the ledger. Platforms retry webhooks; the trip id makes every retry a no-op.
     *
     * @return false when this trip was already recorded
     */
    public boolean record(Platform p, TripReport r) {
        var worker = workers.find(r.workerId()).orElseThrow(() -> new IllegalArgumentException("Unknown worker " + r.workerId()));
        if (!worker.platforms().contains(p.id())) {
            throw new IllegalArgumentException(worker.id() + " does not work on " + p.name());
        }
        String tripId = p.id() + ":" + r.externalId();
        Boolean created = tx.execute(s -> {
            // Serialise this worker's trips on this platform so streak counting can't double-award.
            jdbc.sql("SELECT pg_advisory_xact_lock(hashtext(?))").param(worker.id() + p.id()).query().listOfRows();
            if (jdbc.sql("SELECT EXISTS (SELECT 1 FROM trips WHERE id = ?)").param(tripId).query(Boolean.class).single()) {
                return false;
            }
            int today = jdbc.sql("""
                            SELECT count(*) FROM trips WHERE worker_id = ? AND platform_id = ?
                              AND (completed_at AT TIME ZONE 'Asia/Kolkata')::date = (CAST(? AS timestamptz) AT TIME ZONE 'Asia/Kolkata')::date""")
                    .params(worker.id(), p.id(), Timestamp.from(r.completedAt())).query(Integer.class).single();
            PayCalculator.Result pay = PayCalculator.calculate(p, r.distanceKm(), r.durationMin(), r.waitMin(),
                    r.surge(), r.tipPaise(), today + 1);
            jdbc.sql("""
                            INSERT INTO trips (id, platform_id, worker_id, kind, distance_km, duration_min, wait_min, surge,
                                               fare_paise, incentive_paise, commission_paise, tip_paise, net_paise, breakdown,
                                               pickup_area, drop_area, completed_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                    .params(tripId, p.id(), worker.id(), p.kind(), r.distanceKm(), r.durationMin(), r.waitMin(), r.surge(),
                            pay.farePaise(), pay.incentivePaise(), pay.commissionPaise(), pay.tipPaise(), pay.netPaise(),
                            json.write(pay.lines()), r.pickupArea(), r.dropArea(), Timestamp.from(r.completedAt()))
                    .update();
            outbox.add(Topics.EARNINGS_ACCRUED, worker.id(), new EarningAccrued(UUID.randomUUID().toString(), tripId,
                    worker.id(), p.id(), pay.farePaise(), pay.incentivePaise(), pay.commissionPaise(), pay.tipPaise(),
                    pay.netPaise(), r.completedAt()));
            return true;
        });
        meters.counter("trips.recorded", "platform", p.id(), "result", Boolean.TRUE.equals(created) ? "new" : "duplicate").increment();
        return Boolean.TRUE.equals(created);
    }
}
