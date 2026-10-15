package com.gigpay.contracts;

import java.time.Instant;

public record PayoutEvent(
        String eventId,
        String payoutId,
        String workerId,
        Kind kind,
        Status status,
        long amountPaise,
        long feePaise,
        String reason,
        Instant at) {

    public enum Kind { INSTANT, WEEKLY }

    public enum Status { REQUESTED, SUCCEEDED, FAILED }
}
