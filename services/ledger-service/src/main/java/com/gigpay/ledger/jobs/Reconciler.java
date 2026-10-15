package com.gigpay.ledger.jobs;

import com.gigpay.ledger.ledger.Accounts;
import com.gigpay.ledger.ledger.Ledger;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Compares GigPay's books with the bank's, the way a finance team does every morning:
 * <ul>
 *   <li>every payout the bank says it paid is SUCCEEDED in our ledger, and vice versa;</li>
 *   <li>amounts agree;</li>
 *   <li>the in-transit clearing account equals exactly the payouts still in flight;</li>
 *   <li>the trial balance is zero.</li>
 * </ul>
 */
@Component
public class Reconciler {

    private final JdbcClient jdbc;
    private final Ledger ledger;
    private final AtomicReference<Report> last = new AtomicReference<>();

    public Reconciler(JdbcClient jdbc, Ledger ledger) {
        this.jdbc = jdbc;
        this.ledger = ledger;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 20_000)
    public Report run() {
        List<Issue> issues = new ArrayList<>();
        // Ignore the last 30 s: a webhook may legitimately still be on its way. A payout can have
        // several bank transfers (one per attempt), but at most one of them may succeed.
        jdbc.sql("""
                        SELECT t.reference, t.amount AS bank_amount, t.status AS bank_status, p.id AS payout_id,
                               p.amount AS our_amount, p.status AS our_status
                        FROM provider_transfers t
                        LEFT JOIN payouts p ON p.id = coalesce(substring(t.reference FROM '^(.*)\\.[0-9]+$'), t.reference)
                        WHERE t.created_at < now() - interval '30 seconds'""")
                .query().listOfRows().forEach(r -> {
                    String ref = (String) r.get("reference");
                    String bank = (String) r.get("bank_status");
                    String ours = (String) r.get("our_status");
                    if (ours == null) {
                        issues.add(new Issue("UNKNOWN_TRANSFER", ref, "Bank has a transfer we never requested"));
                    } else if ("SUCCESS".equals(bank) && !"SUCCEEDED".equals(ours)) {
                        issues.add(new Issue("BANK_PAID_LEDGER_NOT", ref, "Bank paid, ledger says " + ours));
                    } else if (((Number) r.get("bank_amount")).longValue() != ((Number) r.get("our_amount")).longValue()) {
                        issues.add(new Issue("AMOUNT_MISMATCH", ref, "Bank " + r.get("bank_amount") + " vs ledger " + r.get("our_amount")));
                    }
                });
        jdbc.sql("""
                        SELECT p.id, count(t.reference) FILTER (WHERE t.status = 'SUCCESS') AS paid
                        FROM payouts p
                        LEFT JOIN provider_transfers t ON coalesce(substring(t.reference FROM '^(.*)\\.[0-9]+$'), t.reference) = p.id
                        WHERE p.status = 'SUCCEEDED' AND p.completed_at < now() - interval '30 seconds'
                        GROUP BY p.id HAVING count(t.reference) FILTER (WHERE t.status = 'SUCCESS') <> 1""")
                .query().listOfRows().forEach(r -> {
                    long paid = ((Number) r.get("paid")).longValue();
                    issues.add(paid == 0
                            ? new Issue("LEDGER_PAID_BANK_NOT", (String) r.get("id"), "Ledger says paid, the bank has no successful transfer")
                            : new Issue("PAID_TWICE", (String) r.get("id"), "The bank paid this payout " + paid + " times"));
                });
        long inFlight = jdbc.sql("SELECT coalesce(sum(amount), 0) FROM payouts WHERE status IN ('CREATED','SUBMITTED','RETRY')")
                .query(Long.class).single();
        long clearing = -ledger.balance(Accounts.IN_TRANSIT);
        if (inFlight != clearing) {
            issues.add(new Issue("CLEARING_MISMATCH", Accounts.IN_TRANSIT,
                    "In-transit account " + clearing + " but payouts in flight total " + inFlight));
        }
        Ledger.TrialBalance tb = ledger.trialBalance();
        if (!tb.balanced()) issues.add(new Issue("TRIAL_BALANCE", "ledger", "Books do not balance: " + tb));
        Map<String, Object> counts = jdbc.sql("""
                        SELECT count(*) AS transfers, count(*) FILTER (WHERE status = 'SUCCESS') AS succeeded,
                               coalesce(sum(amount) FILTER (WHERE status = 'SUCCESS'), 0) AS paid
                        FROM provider_transfers""").query().singleRow();
        Report report = new Report(Instant.now(), ((Number) counts.get("transfers")).longValue(),
                ((Number) counts.get("succeeded")).longValue(), ((Number) counts.get("paid")).longValue(), inFlight,
                tb, issues);
        last.set(report);
        return report;
    }

    public Report lastReport() {
        Report r = last.get();
        return r != null ? r : run();
    }

    public record Issue(String type, String reference, String detail) {
    }

    public record Report(Instant ranAt, long bankTransfers, long bankSucceeded, long bankPaidPaise, long inFlightPaise,
                         Ledger.TrialBalance trialBalance, List<Issue> issues) {
    }
}
