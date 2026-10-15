package com.gigpay.ledger.jobs;

import com.gigpay.contracts.EarningAccrued;
import com.gigpay.contracts.Topics;
import com.gigpay.ledger.config.JsonCodec;
import com.gigpay.ledger.ledger.Accounts;
import com.gigpay.ledger.ledger.Leg;
import com.gigpay.ledger.ledger.Ledger;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * Every completed trip becomes money the worker can access: the platform now owes GigPay, and
 * GigPay owes the worker. The trip id is the idempotency key, so redelivered events post once.
 */
@Component
public class AccrualConsumer {

    private static final NumberFormat INR = NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));

    private final Ledger ledger;
    private final JsonCodec json;
    private final TransactionTemplate tx;

    public AccrualConsumer(Ledger ledger, JsonCodec json, TransactionTemplate tx) {
        this.ledger = ledger;
        this.json = json;
        this.tx = tx;
    }

    @KafkaListener(topics = Topics.EARNINGS_ACCRUED, groupId = "ledger", concurrency = "3")
    public void onEarning(String message) {
        EarningAccrued e = json.read(message, EarningAccrued.class);
        if (e.netPaise() <= 0) return;
        String worker = Accounts.workerPayable(e.workerId());
        String platform = Accounts.platformReceivable(e.platform());
        ledger.ensureAccount(worker, "Owed to worker " + e.workerId(), "LIABILITY", e.workerId());
        ledger.ensureAccount(platform, "Receivable from " + e.platform(), "ASSET", e.platform());
        tx.executeWithoutResult(s -> ledger.post("accrual:" + e.tripId(), "EARNING_ACCRUED",
                e.platform() + " trip " + e.tripId() + " · " + INR.format(e.netPaise() / 100.0), e.tripId(),
                List.of(Leg.debit(platform, e.netPaise()), Leg.credit(worker, e.netPaise()))));
    }
}
