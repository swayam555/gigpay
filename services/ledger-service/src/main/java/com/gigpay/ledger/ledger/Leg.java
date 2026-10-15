package com.gigpay.ledger.ledger;

/**
 * One side of a journal entry. Positive amounts are debits, negative are credits (paise).
 * Asset accounts grow with debits; liability, equity and income accounts grow with credits.
 */
public record Leg(String accountCode, long amount) {

    public static Leg debit(String account, long paise) {
        return new Leg(account, paise);
    }

    public static Leg credit(String account, long paise) {
        return new Leg(account, -paise);
    }
}
