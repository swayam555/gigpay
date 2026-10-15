package com.gigpay.ledger.payout;

import java.time.OffsetDateTime;

public record Payout(String id, String workerId, String kind, long amount, long fee, String upiId, String status,
                     String providerRef, int attempts, String failureReason, OffsetDateTime createdAt,
                     OffsetDateTime submittedAt, OffsetDateTime completedAt, int bankAttempt) {

    /** The reference the bank knows this attempt by: stable across network retries, new per bank attempt. */
    public String bankReference() {
        return bankReference(id, bankAttempt);
    }

    public static String bankReference(String payoutId, int bankAttempt) {
        return payoutId + "." + bankAttempt;
    }

    /** The payout a bank reference belongs to. */
    public static String payoutId(String bankReference) {
        int dot = bankReference.lastIndexOf('.');
        return dot < 0 ? bankReference : bankReference.substring(0, dot);
    }

    /**
     * CREATED → SUBMITTED → SUCCEEDED, or → RETRY → SUBMITTED (transient bank errors, with backoff),
     * or → FAILED (money returned to the worker by a reversing entry).
     */
    public enum Status { CREATED, SUBMITTED, RETRY, SUCCEEDED, FAILED }
}
