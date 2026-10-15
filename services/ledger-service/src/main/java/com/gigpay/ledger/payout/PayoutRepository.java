package com.gigpay.ledger.payout;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class PayoutRepository {

    static final String COLUMNS = """
            id, worker_id, kind, amount, fee, upi_id, status, provider_ref, attempts, failure_reason, created_at,
            submitted_at, completed_at, bank_attempt""";

    private final JdbcClient jdbc;

    public PayoutRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(String id, String idempotencyKey, String workerId, String kind, long amount, long fee, String upiId) {
        jdbc.sql("""
                        INSERT INTO payouts (id, idempotency_key, worker_id, kind, amount, fee, upi_id, status)
                        VALUES (?, ?, ?, ?, ?, ?, ?, 'CREATED')""")
                .params(id, idempotencyKey, workerId, kind, amount, fee, upiId).update();
    }

    public Optional<Payout> find(String id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM payouts WHERE id = ?").param(id).query(Payout.class).optional();
    }

    public Optional<Payout> findForUpdate(String id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM payouts WHERE id = ? FOR UPDATE").param(id).query(Payout.class).optional();
    }

    public Optional<Payout> byIdempotencyKey(String key) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM payouts WHERE idempotency_key = ?").param(key).query(Payout.class).optional();
    }

    /** Claims due work; SKIP LOCKED lets several replicas process payouts in parallel without overlap. */
    public List<Payout> lockDue(int limit) {
        return jdbc.sql("SELECT " + COLUMNS + """
                         FROM payouts WHERE status IN ('CREATED', 'RETRY') AND next_attempt_at <= now()
                        ORDER BY next_attempt_at LIMIT ? FOR UPDATE SKIP LOCKED""")
                .param(limit).query(Payout.class).list();
    }

    public List<Payout> lockStuck(int olderThanSeconds, int limit) {
        return jdbc.sql("SELECT " + COLUMNS + """
                         FROM payouts WHERE status = 'SUBMITTED' AND submitted_at < now() - make_interval(secs => ?)
                        ORDER BY submitted_at LIMIT ? FOR UPDATE SKIP LOCKED""")
                .params(olderThanSeconds, limit).query(Payout.class).list();
    }

    public void markSubmitted(String id, String providerRef) {
        jdbc.sql("UPDATE payouts SET status = 'SUBMITTED', provider_ref = ?, attempts = attempts + 1, submitted_at = now() WHERE id = ?")
                .params(providerRef, id).update();
    }

    /**
     * @param countAttempt   true when this try never reached the bank's books (429, timeout), so it
     *                       hasn't been counted by {@link #markSubmitted} yet
     * @param newBankAttempt true when the bank definitively failed the last attempt, so the next one
     *                       needs a fresh bank reference
     */
    public void markRetry(String id, String reason, Instant next, boolean countAttempt, boolean newBankAttempt) {
        jdbc.sql("""
                        UPDATE payouts SET status = 'RETRY', failure_reason = ?, next_attempt_at = ?,
                               attempts = attempts + ?, bank_attempt = bank_attempt + ?
                        WHERE id = ?""")
                .params(reason, java.sql.Timestamp.from(next), countAttempt ? 1 : 0, newBankAttempt ? 1 : 0, id).update();
    }

    public void markCompleted(String id, Payout.Status status, String reason) {
        jdbc.sql("UPDATE payouts SET status = ?, failure_reason = ?, completed_at = now() WHERE id = ?")
                .params(status.name(), reason, id).update();
    }

    public List<Payout> forWorker(String workerId, int limit) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM payouts WHERE worker_id = ? ORDER BY created_at DESC LIMIT ?")
                .params(workerId, limit).query(Payout.class).list();
    }

    public List<Payout> recent(String status, int limit) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM payouts WHERE (CAST(:s AS TEXT) IS NULL OR status = :s) ORDER BY created_at DESC LIMIT :l")
                .param("s", status).param("l", limit).query(Payout.class).list();
    }

    public TodayUsage todayInstant(String workerId) {
        return jdbc.sql("""
                        SELECT count(*) AS count, coalesce(sum(amount), 0) AS total FROM payouts
                        WHERE worker_id = ? AND kind = 'INSTANT' AND status <> 'FAILED'
                          AND created_at >= date_trunc('day', now() AT TIME ZONE 'Asia/Kolkata') AT TIME ZONE 'Asia/Kolkata'""")
                .param(workerId).query(TodayUsage.class).single();
    }

    public boolean hasActive(String workerId) {
        return Boolean.TRUE.equals(jdbc.sql("SELECT EXISTS (SELECT 1 FROM payouts WHERE worker_id = ? AND status IN ('CREATED','SUBMITTED','RETRY'))")
                .param(workerId).query(Boolean.class).single());
    }

    public record TodayUsage(long count, long total) {
    }
}
