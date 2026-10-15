package com.gigpay.contracts;

/** Kafka topics, keyed by worker id so each worker's money events stay in order. */
public final class Topics {
    /** A completed trip's net earnings, from earnings-service to the ledger. */
    public static final String EARNINGS_ACCRUED = "earnings.accrued";
    /** Payout lifecycle (requested, paid, failed), from the ledger to anyone who cares. */
    public static final String PAYOUT_EVENTS = "payout.events";

    private Topics() {
    }
}
