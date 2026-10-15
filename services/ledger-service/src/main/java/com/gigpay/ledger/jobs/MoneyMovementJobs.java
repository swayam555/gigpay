package com.gigpay.ledger.jobs;

import com.gigpay.ledger.config.LedgerProperties;
import com.gigpay.ledger.ledger.Accounts;
import com.gigpay.ledger.ledger.Leg;
import com.gigpay.ledger.ledger.Ledger;
import com.gigpay.ledger.payout.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

/**
 * The money that flows on a schedule:
 * <ul>
 *   <li>founders' capital seeds the payout float once;</li>
 *   <li>platforms settle what they owe ("weekly"; every 2 minutes in the demo);</li>
 *   <li>workers get a free payout of their remaining balance ("Monday"; every 6 hours in the demo, or on demand from the finance console).</li>
 * </ul>
 * The float funds instant payouts in between: that gap is the working capital an EWA business needs.
 */
@Component
public class MoneyMovementJobs {

    private static final Logger log = LoggerFactory.getLogger(MoneyMovementJobs.class);

    private final Ledger ledger;
    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final LedgerProperties props;
    private final WalletService wallets;
    private final WorkerDirectory workers;

    public MoneyMovementJobs(Ledger ledger, JdbcClient jdbc, TransactionTemplate tx, LedgerProperties props,
                             WalletService wallets, WorkerDirectory workers) {
        this.ledger = ledger;
        this.jdbc = jdbc;
        this.tx = tx;
        this.props = props;
        this.wallets = wallets;
        this.workers = workers;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedCapital() {
        tx.executeWithoutResult(s -> ledger.post("capital-seed", "CAPITAL", "Founders' capital into the payout float",
                null, List.of(Leg.debit(Accounts.BANK_FLOAT, props.seedCapital()), Leg.credit(Accounts.CAPITAL, props.seedCapital()))));
    }

    @Scheduled(fixedDelayString = "${gigpay.settlement-every:120s}", initialDelayString = "${gigpay.settlement-every:120s}")
    public void settlePlatforms() {
        long cycle = System.currentTimeMillis() / props.settlementEvery().toMillis();
        List<Map<String, Object>> receivables = jdbc.sql("""
                        SELECT code, owner_id, balance FROM accounts WHERE code LIKE 'platform:%:receivable' AND balance > 0""")
                .query().listOfRows();
        for (Map<String, Object> r : receivables) {
            String code = (String) r.get("code");
            long amount = ((Number) r.get("balance")).longValue();
            tx.executeWithoutResult(s -> ledger.post("settlement:" + code + ":" + cycle, "PLATFORM_SETTLEMENT",
                    r.get("owner_id") + " settled its weekly payable", null,
                    List.of(Leg.debit(Accounts.BANK_FLOAT, amount), Leg.credit(code, amount))));
            log.info("Platform {} settled ₹{}", r.get("owner_id"), amount / 100);
        }
    }

    @Scheduled(fixedDelayString = "${gigpay.weekly-payout-every:6h}", initialDelayString = "${gigpay.weekly-payout-every:6h}")
    public void weeklyPayouts() {
        String cycle = String.valueOf(System.currentTimeMillis() / props.weeklyPayoutEvery().toMillis());
        List<String> owing = jdbc.sql("""
                        SELECT owner_id FROM accounts WHERE code LIKE 'worker:%:payable' AND -balance >= ?""")
                .param(props.minPayout()).query(String.class).list();
        int started = 0;
        for (String workerId : owing) {
            try {
                String upi = workers.payoutUpi(workerId);
                if (upi == null) continue;
                wallets.weeklyPayout(workerId, upi, cycle);
                started++;
            } catch (RuntimeException e) {
                log.warn("Weekly payout skipped for {}: {}", workerId, e.getMessage());
            }
        }
        if (started > 0) log.info("Weekly payout cycle {}: {} workers", cycle, started);
    }
}
