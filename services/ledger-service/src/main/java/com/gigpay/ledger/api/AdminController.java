package com.gigpay.ledger.api;

import com.gigpay.ledger.config.JsonCodec;
import com.gigpay.ledger.jobs.MoneyMovementJobs;
import com.gigpay.ledger.jobs.Reconciler;
import com.gigpay.ledger.ledger.Accounts;
import com.gigpay.ledger.ledger.Ledger;
import com.gigpay.ledger.payout.Payout;
import com.gigpay.ledger.payout.PayoutRepository;
import com.gigpay.ledger.provider.ProviderFaults;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Finance console API. The gateway only lets ADMIN tokens reach {@code /api/ledger/admin/**}. */
@RestController
@RequestMapping("/api/ledger/admin")
public class AdminController {

    private static final TypeReference<List<JournalLeg>> LEGS = new TypeReference<>() {
    };

    private final Ledger ledger;
    private final JdbcClient jdbc;
    private final PayoutRepository payouts;
    private final Reconciler reconciler;
    private final ProviderFaults faults;
    private final MoneyMovementJobs jobs;
    private final JsonCodec json;

    public AdminController(Ledger ledger, JdbcClient jdbc, PayoutRepository payouts, Reconciler reconciler,
                           ProviderFaults faults, MoneyMovementJobs jobs, JsonCodec json) {
        this.ledger = ledger;
        this.jdbc = jdbc;
        this.payouts = payouts;
        this.reconciler = reconciler;
        this.faults = faults;
        this.jobs = jobs;
        this.json = json;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("bankFloat", ledger.balance(Accounts.BANK_FLOAT));
        body.put("owedToWorkers", -jdbc.sql("SELECT coalesce(sum(balance), 0) FROM accounts WHERE code LIKE 'worker:%:payable'")
                .query(Long.class).single());
        body.put("receivableFromPlatforms", jdbc.sql("SELECT coalesce(sum(balance), 0) FROM accounts WHERE code LIKE 'platform:%:receivable'")
                .query(Long.class).single());
        body.put("inTransit", -ledger.balance(Accounts.IN_TRANSIT));
        body.put("feeIncome", -ledger.balance(Accounts.FEE_INCOME));
        body.put("capital", -ledger.balance(Accounts.CAPITAL));
        body.put("receivablesByPlatform", jdbc.sql("""
                        SELECT owner_id AS platform, balance FROM accounts WHERE code LIKE 'platform:%:receivable' ORDER BY owner_id""")
                .query().listOfRows());
        body.put("payoutsByStatus", jdbc.sql("""
                        SELECT status, kind, count(*) AS count, coalesce(sum(amount), 0) AS amount FROM payouts
                        WHERE created_at > now() - interval '1 hour' GROUP BY status, kind ORDER BY status""")
                .query().listOfRows());
        body.put("medianSecondsToMoney", jdbc.sql("""
                        SELECT coalesce(percentile_cont(0.5) WITHIN GROUP (ORDER BY extract(epoch FROM completed_at - created_at)), 0)
                        FROM payouts WHERE status = 'SUCCEEDED' AND kind = 'INSTANT' AND created_at > now() - interval '1 hour'""")
                .query(Double.class).single());
        body.put("workersWithBalance", jdbc.sql("SELECT count(*) FROM accounts WHERE code LIKE 'worker:%:payable' AND balance < 0")
                .query(Long.class).single());
        body.put("trialBalance", ledger.trialBalance());
        return body;
    }

    @GetMapping("/accounts")
    public List<Ledger.AccountRow> accounts(@RequestParam(required = false) String type,
                                            @RequestParam(defaultValue = "50") int limit) {
        return ledger.accounts(type, Math.min(limit, 500));
    }

    @GetMapping("/journal")
    public List<JournalEntry> journal(@RequestParam(defaultValue = "40") int limit) {
        return jdbc.sql("""
                        SELECT e.id, e.kind, e.description, e.created_at,
                               json_agg(json_build_object('account', a.code, 'amount', p.amount) ORDER BY p.amount DESC)::text AS legs
                        FROM journal_entries e JOIN postings p ON p.entry_id = e.id JOIN accounts a ON a.id = p.account_id
                        GROUP BY e.id ORDER BY e.id DESC LIMIT ?""").param(Math.min(limit, 200))
                .query((rs, n) -> new JournalEntry(rs.getLong("id"), rs.getString("kind"), rs.getString("description"),
                        rs.getObject("created_at", OffsetDateTime.class), json.read(rs.getString("legs"), LEGS)))
                .list();
    }

    @GetMapping("/payouts")
    public List<Payout> payouts(@RequestParam(required = false) String status, @RequestParam(defaultValue = "50") int limit) {
        return payouts.recent(status, Math.min(limit, 300));
    }

    @GetMapping("/reconciliation")
    public Reconciler.Report reconciliation() {
        return reconciler.lastReport();
    }

    @PostMapping("/reconciliation/run")
    public Reconciler.Report reconcile() {
        return reconciler.run();
    }

    @GetMapping("/faults")
    public ProviderFaults.Faults faults() {
        return faults.current();
    }

    @PutMapping("/faults")
    public ProviderFaults.Faults setFaults(@RequestBody ProviderFaults.Faults f) {
        return faults.update(f);
    }

    @PostMapping("/jobs/settle-platforms")
    public Map<String, String> settleNow() {
        jobs.settlePlatforms();
        return Map.of("status", "done");
    }

    @PostMapping("/jobs/weekly-payout")
    public Map<String, String> weeklyNow() {
        jobs.weeklyPayouts();
        return Map.of("status", "done");
    }

    public record JournalLeg(String account, long amount) {
    }

    public record JournalEntry(long id, String kind, String description, OffsetDateTime createdAt, List<JournalLeg> legs) {
    }
}
