package com.gigpay.ledger.payout;

import com.gigpay.contracts.PayoutEvent;
import com.gigpay.contracts.Topics;
import com.gigpay.ledger.config.LedgerProperties;
import com.gigpay.ledger.ledger.Accounts;
import com.gigpay.ledger.ledger.Leg;
import com.gigpay.ledger.ledger.Ledger;
import com.gigpay.ledger.outbox.Outbox;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Earned wage access: a worker can take money they have already earned, instantly, instead of
 * waiting for the weekly payout. This is not a loan: no interest, no credit check, a flat fee.
 */
@Service
public class WalletService {

    private static final Pattern UPI = Pattern.compile("^[a-zA-Z0-9.\\-_]{2,64}@[a-zA-Z]{2,32}$");

    private final Ledger ledger;
    private final PayoutRepository payouts;
    private final Outbox outbox;
    private final TransactionTemplate tx;
    private final LedgerProperties props;

    public WalletService(Ledger ledger, PayoutRepository payouts, Outbox outbox, TransactionTemplate tx,
                         LedgerProperties props) {
        this.ledger = ledger;
        this.payouts = payouts;
        this.outbox = outbox;
        this.tx = tx;
        this.props = props;
    }

    public Wallet wallet(String workerId) {
        long owed = -ledger.balance(Accounts.workerPayable(workerId));
        PayoutRepository.TodayUsage today = payouts.todayInstant(workerId);
        return new Wallet(workerId, owed, Limits.withdrawable(owed, today, props), props.instantFee(), props.minPayout(),
                props.dailyInstantLimit() - today.total(), props.dailyInstantCount() - today.count(),
                Math.round(props.withdrawableShare() * 100));
    }

    /**
     * Takes an instant payout. The worker's payable account row is locked for the whole decision,
     * so two taps on "Withdraw" (or two devices) can never both spend the same balance.
     */
    public Payout withdraw(String workerId, long amount, String upiId, String idempotencyKey) {
        if (idempotencyKey != null) {
            var previous = payouts.byIdempotencyKey(idempotencyKey);
            if (previous.isPresent()) return previous.get();
        }
        if (upiId == null || !UPI.matcher(upiId).matches()) throw new IllegalArgumentException("Enter a valid UPI ID");
        String payoutId = "po_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String account = Accounts.workerPayable(workerId);
        // A brand-new worker may tap "Withdraw" before their first trip has settled.
        ledger.ensureAccount(account, "Owed to worker " + workerId, "LIABILITY", workerId);
        tx.executeWithoutResult(s -> {
            long owed = -ledger.lockAndGetBalance(account, List.of(Accounts.IN_TRANSIT, Accounts.FEE_INCOME));
            PayoutRepository.TodayUsage today = payouts.todayInstant(workerId);
            Limits.check(amount, owed, today, props);
            payouts.insert(payoutId, idempotencyKey, workerId, "INSTANT", amount, props.instantFee(), upiId);
            ledger.post("payout:" + payoutId, "PAYOUT_REQUESTED", "Instant payout to " + upiId, payoutId,
                    feeLegs(account, amount, props.instantFee()));
            outbox.add(Topics.PAYOUT_EVENTS, workerId, new PayoutEvent(UUID.randomUUID().toString(), payoutId,
                    workerId, PayoutEvent.Kind.INSTANT, PayoutEvent.Status.REQUESTED, amount, props.instantFee(), null,
                    Instant.now()));
        });
        return payouts.find(payoutId).orElseThrow();
    }

    /** Free end-of-week payout of the whole balance. */
    public void weeklyPayout(String workerId, String upiId, String cycle) {
        String account = Accounts.workerPayable(workerId);
        tx.executeWithoutResult(s -> {
            long owed = -ledger.lockAndGetBalance(account, List.of(Accounts.IN_TRANSIT));
            if (owed < props.minPayout() || payouts.hasActive(workerId)) return;
            String payoutId = "pw_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            payouts.insert(payoutId, "weekly:" + cycle + ":" + workerId, workerId, "WEEKLY", owed, 0, upiId);
            ledger.post("payout:" + payoutId, "PAYOUT_REQUESTED", "Weekly payout to " + upiId, payoutId,
                    List.of(Leg.debit(account, owed), Leg.credit(Accounts.IN_TRANSIT, owed)));
            outbox.add(Topics.PAYOUT_EVENTS, workerId, new PayoutEvent(UUID.randomUUID().toString(), payoutId,
                    workerId, PayoutEvent.Kind.WEEKLY, PayoutEvent.Status.REQUESTED, owed, 0, null, Instant.now()));
        });
    }

    /** The worker's balance goes down by amount + fee; the bank owes the amount; the fee is income. */
    static List<Leg> feeLegs(String workerAccount, long amount, long fee) {
        return fee == 0
                ? List.of(Leg.debit(workerAccount, amount), Leg.credit(Accounts.IN_TRANSIT, amount))
                : List.of(Leg.debit(workerAccount, amount + fee), Leg.credit(Accounts.IN_TRANSIT, amount),
                Leg.credit(Accounts.FEE_INCOME, fee));
    }

    /**
     * @param owed               earned and not yet paid out
     * @param withdrawableNow    what an instant payout can send right now (fee already accounted for)
     */
    public record Wallet(String workerId, long owed, long withdrawableNow, long instantFee, long minPayout,
                         long dailyLimitLeft, long payoutsLeftToday, long withdrawablePercent) {
    }
}
