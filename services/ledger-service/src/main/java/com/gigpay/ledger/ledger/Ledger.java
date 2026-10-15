package com.gigpay.ledger.ledger;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * The double-entry ledger.
 *
 * <ul>
 *   <li><b>Balanced:</b> every entry's legs sum to exactly zero, or nothing is written.</li>
 *   <li><b>Idempotent:</b> each entry has a unique idempotency key; posting it twice is a no-op,
 *       which makes Kafka redelivery and client retries harmless.</li>
 *   <li><b>Append-only:</b> postings are never updated or deleted; mistakes are fixed with
 *       reversing entries, so the history is a complete audit trail.</li>
 *   <li><b>Serialised per account:</b> involved accounts are locked in id order
 *       ({@code SELECT … FOR UPDATE}), so concurrent entries can't lose updates and can't deadlock.</li>
 * </ul>
 */
@Service
public class Ledger {

    private final JdbcClient jdbc;

    public Ledger(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void ensureAccount(String code, String name, String type, String ownerId) {
        jdbc.sql("INSERT INTO accounts (code, name, type, owner_id) VALUES (?, ?, ?, ?) ON CONFLICT (code) DO NOTHING")
                .params(code, name, type, ownerId).update();
    }

    /** Posts an entry inside the caller's transaction (or a new one). Returns false if it already existed. */
    @Transactional(propagation = Propagation.REQUIRED)
    public boolean post(String idempotencyKey, String kind, String description, String reference, List<Leg> legs) {
        if (legs.size() < 2) throw new IllegalArgumentException("An entry needs at least two legs");
        long sum = 0;
        Map<String, Long> byAccount = new LinkedHashMap<>();
        for (Leg leg : legs) {
            if (leg.amount() == 0) throw new IllegalArgumentException("Zero-amount leg on " + leg.accountCode());
            sum = Math.addExact(sum, leg.amount());
            byAccount.merge(leg.accountCode(), leg.amount(), Math::addExact);
        }
        if (sum != 0) throw new UnbalancedEntryException(idempotencyKey, sum);

        Optional<Long> entryId = jdbc.sql("""
                        INSERT INTO journal_entries (idempotency_key, kind, description, reference)
                        VALUES (?, ?, ?, ?) ON CONFLICT (idempotency_key) DO NOTHING RETURNING id""")
                .params(idempotencyKey, kind, description, reference).query(Long.class).optional();
        if (entryId.isEmpty()) return false;

        Map<String, Long> ids = lockInIdOrder(byAccount.keySet());
        for (Leg leg : legs) {
            long accountId = ids.get(leg.accountCode());
            jdbc.sql("INSERT INTO postings (entry_id, account_id, amount) VALUES (?, ?, ?)")
                    .params(entryId.get(), accountId, leg.amount()).update();
        }
        byAccount.forEach((code, delta) -> jdbc.sql("UPDATE accounts SET balance = balance + ? WHERE id = ?")
                .params(delta, ids.get(code)).update());
        return true;
    }

    public long balance(String code) {
        return jdbc.sql("SELECT balance FROM accounts WHERE code = ?").param(code).query(Long.class).optional().orElse(0L);
    }

    /**
     * Locks every account an upcoming entry will touch, in id order, and returns the balance of
     * {@code code}. Taking only the first account's lock and the rest later inside {@link #post}
     * would lock rows in a different order from other entries, and deadlock against them.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public long lockAndGetBalance(String code, Collection<String> alsoTouched) {
        Set<String> codes = new TreeSet<>(alsoTouched);
        codes.add(code);
        lockInIdOrder(codes);
        return balance(code);
    }

    private Map<String, Long> lockInIdOrder(Collection<String> accountCodes) {
        List<String> codes = new ArrayList<>(new TreeSet<>(accountCodes));
        Map<String, Long> ids = new LinkedHashMap<>();
        jdbc.sql("SELECT id, code FROM accounts WHERE code IN (:codes) ORDER BY id FOR UPDATE")
                .param("codes", codes)
                .query((rs, n) -> ids.put(rs.getString("code"), rs.getLong("id")))
                .list();
        for (String code : codes) {
            if (!ids.containsKey(code)) throw new IllegalArgumentException("Unknown account " + code);
        }
        return ids;
    }

    public List<Line> statement(String code, int limit) {
        return jdbc.sql("""
                        SELECT e.id AS entry_id, e.kind, e.description, e.reference, p.amount, p.created_at
                        FROM postings p
                        JOIN journal_entries e ON e.id = p.entry_id
                        JOIN accounts a ON a.id = p.account_id
                        WHERE a.code = ?
                        ORDER BY p.id DESC LIMIT ?""")
                .params(code, limit).query(Line.class).list();
    }

    public List<AccountRow> accounts(String typeFilter, int limit) {
        return jdbc.sql("""
                        SELECT code, name, type, owner_id, balance FROM accounts
                        WHERE (CAST(:type AS TEXT) IS NULL OR type = :type)
                        ORDER BY abs(balance) DESC LIMIT :limit""")
                .param("type", typeFilter).param("limit", limit).query(AccountRow.class).list();
    }

    /**
     * The three invariants an auditor would check first. All must be zero, always.
     */
    public TrialBalance trialBalance() {
        Long balances = jdbc.sql("SELECT coalesce(sum(balance), 0) FROM accounts").query(Long.class).single();
        Long postings = jdbc.sql("SELECT coalesce(sum(amount), 0) FROM postings").query(Long.class).single();
        Long unbalanced = jdbc.sql("""
                        SELECT count(*) FROM (SELECT entry_id FROM postings GROUP BY entry_id HAVING sum(amount) <> 0) x""")
                .query(Long.class).single();
        Long drift = jdbc.sql("""
                        SELECT count(*) FROM accounts a
                        WHERE a.balance <> (SELECT coalesce(sum(amount), 0) FROM postings p WHERE p.account_id = a.id)""")
                .query(Long.class).single();
        Long entries = jdbc.sql("SELECT count(*) FROM journal_entries").query(Long.class).single();
        return new TrialBalance(balances, postings, unbalanced, drift, entries,
                balances == 0 && postings == 0 && unbalanced == 0 && drift == 0);
    }

    public record Line(long entryId, String kind, String description, String reference, long amount,
                       OffsetDateTime createdAt) {
    }

    public record AccountRow(String code, String name, String type, String ownerId, long balance) {
    }

    /**
     * @param sumOfBalances         must be 0: debits equal credits across the whole book
     * @param sumOfPostings         must be 0
     * @param unbalancedEntries     entries whose postings don't sum to zero: must be 0
     * @param accountsWithDrift     cached balances that differ from their postings: must be 0
     */
    public record TrialBalance(long sumOfBalances, long sumOfPostings, long unbalancedEntries, long accountsWithDrift,
                               long entries, boolean balanced) {
    }

    public static class UnbalancedEntryException extends IllegalArgumentException {
        public UnbalancedEntryException(String key, long sum) {
            super("Entry " + key + " does not balance (off by " + sum + " paise)");
        }
    }
}
