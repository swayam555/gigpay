package com.gigpay.ledger.payout;

import com.gigpay.contracts.PayoutEvent;
import com.gigpay.contracts.Topics;
import com.gigpay.ledger.ledger.Accounts;
import com.gigpay.ledger.ledger.Leg;
import com.gigpay.ledger.ledger.Ledger;
import com.gigpay.ledger.outbox.Outbox;
import com.gigpay.ledger.provider.ProviderClient;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Moves money out. Payout rails are asynchronous and unreliable: requests time out, banks are
 * down, callbacks get lost or arrive twice. Every outcome is handled:
 * <ul>
 *   <li>transient failure → retry with exponential backoff (2, 4, 8 s), then give up;</li>
 *   <li>permanent failure → reversing entry, money is back in the worker's balance;</li>
 *   <li>lost callback → a poller asks the provider for the status of anything stuck;</li>
 *   <li>duplicate callback → ignored, a payout completes exactly once.</li>
 * </ul>
 */
@Service
public class PayoutService {

    private static final Logger log = LoggerFactory.getLogger(PayoutService.class);
    static final int MAX_ATTEMPTS = 4;

    private final PayoutRepository payouts;
    private final Ledger ledger;
    private final Outbox outbox;
    private final ProviderClient provider;
    private final TransactionTemplate tx;
    private final MeterRegistry meters;

    public PayoutService(PayoutRepository payouts, Ledger ledger, Outbox outbox, ProviderClient provider,
                         TransactionTemplate tx, MeterRegistry meters) {
        this.payouts = payouts;
        this.ledger = ledger;
        this.outbox = outbox;
        this.provider = provider;
        this.tx = tx;
        this.meters = meters;
    }

    @Scheduled(fixedDelay = 500)
    public void submitDue() {
        List<Payout> claimed = new ArrayList<>();
        tx.executeWithoutResult(s -> claimed.addAll(payouts.lockDue(20)));
        for (Payout p : claimed) submit(p);
    }

    private void submit(Payout p) {
        tx.executeWithoutResult(s -> {
            Payout fresh = payouts.findForUpdate(p.id()).orElseThrow();
            if (!fresh.status().equals("CREATED") && !fresh.status().equals("RETRY")) return;
            ProviderClient.Submission r = provider.transfer(fresh.bankReference(), fresh.amount(), fresh.upiId());
            switch (r.outcome()) {
                case ACCEPTED -> payouts.markSubmitted(fresh.id(), r.providerRef());
                // The bank may or may not have recorded a timed-out request, so the retry keeps the
                // same reference: if it did, the bank recognises the duplicate instead of paying twice.
                case REJECTED_RETRIABLE, UNREACHABLE -> retryOrFail(fresh, r.reason(), true, false);
                case REJECTED_PERMANENT -> fail(fresh, r.reason());
            }
        });
    }

    /**
     * Callback from the provider (or the stuck-payout poller) for one bank attempt. Idempotent: a
     * duplicate, or a late callback about an earlier attempt, is ignored.
     */
    public void onResult(String bankReference, boolean success, boolean retriable, String reason) {
        tx.executeWithoutResult(s -> {
            Payout p = payouts.findForUpdate(Payout.payoutId(bankReference)).orElse(null);
            if (p == null || !p.status().equals("SUBMITTED") || !p.bankReference().equals(bankReference)) return;
            if (success) {
                payouts.markCompleted(p.id(), Payout.Status.SUCCEEDED, null);
                ledger.post("payout-settled:" + p.id(), "PAYOUT_SETTLED", "Bank confirmed payout to " + p.upiId(), p.id(),
                        List.of(Leg.debit(Accounts.IN_TRANSIT, p.amount()), Leg.credit(Accounts.BANK_FLOAT, p.amount())));
                emit(p, PayoutEvent.Status.SUCCEEDED, null);
                meters.timer("payouts.time_to_money").record(Duration.between(p.createdAt().toInstant(), Instant.now()));
                meters.counter("payouts.succeeded", "kind", p.kind()).increment();
            } else if (retriable) {
                // That attempt is dead at the bank; the next one needs a new reference.
                retryOrFail(p, reason, false, true);
            } else {
                fail(p, reason);
            }
        });
    }

    private void retryOrFail(Payout p, String reason, boolean countAttempt, boolean newBankAttempt) {
        int tries = p.attempts() + (countAttempt ? 1 : 0);
        if (tries >= MAX_ATTEMPTS) {
            fail(p, reason + " (gave up after " + MAX_ATTEMPTS + " attempts)");
            return;
        }
        long backoffSeconds = (long) Math.pow(2, tries);
        payouts.markRetry(p.id(), reason, Instant.now().plusSeconds(backoffSeconds), countAttempt, newBankAttempt);
        meters.counter("payouts.retried").increment();
        log.info("Payout {} will retry in {} s: {}", p.id(), backoffSeconds, reason);
    }

    /** Permanent failure: a reversing entry puts the amount and the fee back in the worker's balance. */
    private void fail(Payout p, String reason) {
        payouts.markCompleted(p.id(), Payout.Status.FAILED, reason);
        List<Leg> legs = new ArrayList<>();
        legs.add(Leg.debit(Accounts.IN_TRANSIT, p.amount()));
        if (p.fee() > 0) legs.add(Leg.debit(Accounts.FEE_INCOME, p.fee()));
        legs.add(Leg.credit(Accounts.workerPayable(p.workerId()), p.amount() + p.fee()));
        ledger.post("payout-reversed:" + p.id(), "PAYOUT_REVERSED", "Payout failed (" + reason + "); money returned",
                p.id(), legs);
        emit(p, PayoutEvent.Status.FAILED, reason);
        meters.counter("payouts.failed").increment();
    }

    /** Lost callbacks happen. Anything submitted more than 15 s ago is checked with the provider directly. */
    @Scheduled(fixedDelay = 5000)
    public void pollStuck() {
        List<Payout> stuck = new ArrayList<>();
        tx.executeWithoutResult(s -> stuck.addAll(payouts.lockStuck(15, 50)));
        for (Payout p : stuck) {
            ProviderClient.Status st = provider.status(p.bankReference());
            if (st == null || st.status().equals("PENDING")) continue;
            meters.counter("payouts.recovered_by_polling").increment();
            log.info("Recovered payout {} by polling: {}", p.id(), st.status());
            onResult(p.bankReference(), st.status().equals("SUCCESS"), st.retriable(), st.reason());
        }
    }

    private void emit(Payout p, PayoutEvent.Status status, String reason) {
        outbox.add(Topics.PAYOUT_EVENTS, p.workerId(), new PayoutEvent(UUID.randomUUID().toString(), p.id(),
                p.workerId(), PayoutEvent.Kind.valueOf(p.kind()), status, p.amount(), p.fee(), reason, Instant.now()));
    }
}
