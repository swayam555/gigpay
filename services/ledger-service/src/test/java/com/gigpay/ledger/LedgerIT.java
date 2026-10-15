package com.gigpay.ledger;

import com.gigpay.ledger.config.LedgerProperties;
import com.gigpay.ledger.ledger.Accounts;
import com.gigpay.ledger.ledger.Leg;
import com.gigpay.ledger.ledger.Ledger;
import com.gigpay.ledger.outbox.Outbox;
import com.gigpay.ledger.payout.Limits;
import com.gigpay.ledger.payout.Payout;
import com.gigpay.ledger.payout.PayoutRepository;
import com.gigpay.ledger.payout.PayoutService;
import com.gigpay.ledger.payout.WalletService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Money-safety properties of the ledger, against a real Postgres. */
@Testcontainers
class LedgerIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static JdbcClient jdbc;
    static Ledger ledger;
    static TransactionTemplate tx;
    static WalletService wallets;
    static PayoutRepository payouts;
    static PayoutService payoutService;
    static final LedgerProperties PROPS = new LedgerProperties(0.9, 10_000, 500_000, 3, 500,
            "http://unused", "secret", 100_000_000, Duration.ofSeconds(120), Duration.ofSeconds(300));

    @BeforeAll
    static void setUp() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setURL(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).load().migrate();
        jdbc = JdbcClient.create(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        ledger = new Ledger(jdbc);
        payouts = new PayoutRepository(jdbc);
        Outbox outbox = new Outbox(jdbc, null, new com.gigpay.ledger.config.JsonCodec(tools.jackson.databind.json.JsonMapper.builder().build()));
        wallets = new WalletService(ledger, payouts, outbox, tx, PROPS);
        payoutService = new PayoutService(payouts, ledger, outbox, null, tx, new SimpleMeterRegistry());
        tx.executeWithoutResult(s -> ledger.post("capital-seed", "CAPITAL", "seed", null,
                List.of(Leg.debit(Accounts.BANK_FLOAT, 100_000_000), Leg.credit(Accounts.CAPITAL, 100_000_000))));
    }

    static void earn(String worker, String platform, long paise) {
        ledger.ensureAccount(Accounts.workerPayable(worker), "w", "LIABILITY", worker);
        ledger.ensureAccount(Accounts.platformReceivable(platform), "p", "ASSET", platform);
        tx.executeWithoutResult(s -> ledger.post("accrual:" + UUID.randomUUID(), "EARNING_ACCRUED", "trip", null,
                List.of(Leg.debit(Accounts.platformReceivable(platform), paise), Leg.credit(Accounts.workerPayable(worker), paise))));
    }

    @Test
    void unbalancedEntriesAreRejectedAndNothingIsWritten() {
        long before = jdbc.sql("SELECT count(*) FROM journal_entries").query(Long.class).single();
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> ledger.post("bad", "X", "bad", null,
                List.of(Leg.debit(Accounts.BANK_FLOAT, 100), Leg.credit(Accounts.CAPITAL, 99)))))
                .isInstanceOf(Ledger.UnbalancedEntryException.class);
        assertThat(jdbc.sql("SELECT count(*) FROM journal_entries").query(Long.class).single()).isEqualTo(before);
    }

    @Test
    void postingTheSameEntryTwiceIsANoOp() {
        earn("w-idem", "FoodRunner", 1_000);
        String key = "accrual:fixed-trip-1";
        tx.executeWithoutResult(s -> assertThat(ledger.post(key, "EARNING_ACCRUED", "t", null, List.of(
                Leg.debit(Accounts.platformReceivable("FoodRunner"), 5_000),
                Leg.credit(Accounts.workerPayable("w-idem"), 5_000)))).isTrue());
        tx.executeWithoutResult(s -> assertThat(ledger.post(key, "EARNING_ACCRUED", "t", null, List.of(
                Leg.debit(Accounts.platformReceivable("FoodRunner"), 5_000),
                Leg.credit(Accounts.workerPayable("w-idem"), 5_000)))).isFalse());
        assertThat(-ledger.balance(Accounts.workerPayable("w-idem"))).isEqualTo(6_000);
    }

    @Test
    void concurrentWithdrawalsCannotSpendTheSameMoneyTwice() throws Exception {
        earn("w-race", "RideNow", 100_000); // ₹1,000 earned → ₹900 accessible → one ₹500 payout + ₹5 fee fits, two don't
        ExecutorService pool = Executors.newFixedThreadPool(20);
        List<Callable<Boolean>> taps = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            taps.add(() -> {
                try {
                    wallets.withdraw("w-race", 50_000, "ravi@oksbi", UUID.randomUUID().toString());
                    return true;
                } catch (Limits.LimitExceededException e) {
                    return false;
                }
            });
        }
        int succeeded = 0;
        for (Future<Boolean> f : pool.invokeAll(taps)) if (f.get()) succeeded++;
        pool.shutdown();
        assertThat(succeeded).isEqualTo(1);
        assertThat(-ledger.balance(Accounts.workerPayable("w-race"))).isEqualTo(100_000 - 50_000 - 500);
    }

    @Test
    void sameIdempotencyKeyReturnsTheSamePayout() {
        earn("w-key", "QuickDrop", 200_000);
        Payout a = wallets.withdraw("w-key", 20_000, "worker@ybl", "tap-1");
        Payout b = wallets.withdraw("w-key", 20_000, "worker@ybl", "tap-1");
        assertThat(b.id()).isEqualTo(a.id());
        assertThat(payouts.forWorker("w-key", 10)).hasSize(1);
    }

    @Test
    void failedPayoutIsReversedAndMoneyComesBack() {
        earn("w-fail", "FoodRunner", 100_000);
        Payout p = wallets.withdraw("w-fail", 30_000, "worker@ybl", null);
        jdbc.sql("UPDATE payouts SET status = 'SUBMITTED', submitted_at = now() WHERE id = ?").param(p.id()).update();
        payoutService.onResult(p.bankReference(), false, false, "INVALID_VPA");
        payoutService.onResult(p.bankReference(), false, false, "INVALID_VPA"); // duplicate webhook
        assertThat(payouts.find(p.id()).orElseThrow().status()).isEqualTo("FAILED");
        assertThat(-ledger.balance(Accounts.workerPayable("w-fail"))).isEqualTo(100_000);
        assertThat(ledger.trialBalance().balanced()).isTrue();
    }

    @Test
    void bankFailureIsRetriedUnderANewReferenceAndLateCallbacksAreIgnored() {
        earn("w-retry", "FoodRunner", 100_000);
        long floatBefore = ledger.balance(Accounts.BANK_FLOAT);
        Payout p = wallets.withdraw("w-retry", 30_000, "worker@ybl", null);
        jdbc.sql("UPDATE payouts SET status = 'SUBMITTED', submitted_at = now(), attempts = 1 WHERE id = ?").param(p.id()).update();
        String first = p.bankReference();
        payoutService.onResult(first, false, true, "NPCI_TIMEOUT");
        Payout retrying = payouts.find(p.id()).orElseThrow();
        assertThat(retrying.status()).isEqualTo("RETRY");
        assertThat(retrying.bankReference()).isNotEqualTo(first);

        // The second attempt reaches the bank; a late duplicate of the first attempt's failure changes nothing.
        jdbc.sql("UPDATE payouts SET status = 'SUBMITTED', submitted_at = now(), attempts = 2 WHERE id = ?").param(p.id()).update();
        payoutService.onResult(first, false, true, "NPCI_TIMEOUT");
        assertThat(payouts.find(p.id()).orElseThrow().status()).isEqualTo("SUBMITTED");
        payoutService.onResult(retrying.bankReference(), true, false, null);
        assertThat(payouts.find(p.id()).orElseThrow().status()).isEqualTo("SUCCEEDED");
        assertThat(ledger.balance(Accounts.BANK_FLOAT)).isEqualTo(floatBefore - 30_000);
        assertThat(ledger.trialBalance().balanced()).isTrue();
    }

    @Test
    void successfulPayoutMovesMoneyFromFloatExactlyOnce() {
        earn("w-ok", "RideNow", 100_000);
        long floatBefore = ledger.balance(Accounts.BANK_FLOAT);
        Payout p = wallets.withdraw("w-ok", 40_000, "worker@ybl", null);
        jdbc.sql("UPDATE payouts SET status = 'SUBMITTED', submitted_at = now() WHERE id = ?").param(p.id()).update();
        payoutService.onResult(p.bankReference(), true, false, null);
        payoutService.onResult(p.bankReference(), true, false, null);
        assertThat(ledger.balance(Accounts.BANK_FLOAT)).isEqualTo(floatBefore - 40_000);
    }

    @Test
    void booksStayBalancedThroughARandomStormOfOperations() throws Exception {
        Random rnd = new Random(11);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Callable<Void>> ops = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            String worker = "storm-" + rnd.nextInt(10);
            int kind = rnd.nextInt(3);
            long amount = 5_000 + rnd.nextInt(50_000);
            ops.add(() -> {
                try {
                    if (kind == 0) {
                        earn(worker, "FoodRunner", amount);
                    } else {
                        Payout p = wallets.withdraw(worker, (amount / 100) * 100 + 10_000, "storm@ybl", null);
                        jdbc.sql("UPDATE payouts SET status = 'SUBMITTED', submitted_at = now() WHERE id = ?").param(p.id()).update();
                        payoutService.onResult(p.bankReference(), kind == 1, false, kind == 1 ? null : "BANK_DOWN");
                    }
                } catch (Limits.LimitExceededException ignored) {
                    // expected when a worker has nothing left to take
                }
                return null;
            });
        }
        for (Future<Void> f : pool.invokeAll(ops)) f.get();
        pool.shutdown();
        Ledger.TrialBalance tb = ledger.trialBalance();
        assertThat(tb.balanced()).as(tb.toString()).isTrue();
        // No worker can ever be owed a negative amount (i.e. overdrawn).
        assertThat(jdbc.sql("SELECT count(*) FROM accounts WHERE code LIKE 'worker:%:payable' AND balance > 0")
                .query(Long.class).single()).isZero();
    }
}
