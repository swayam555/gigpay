package com.gigpay.contracts;

import java.time.Instant;

/**
 * Net earnings of one completed trip. All money is in paise (1 rupee = 100 paise) as a long:
 * floating point never touches money in GigPay.
 *
 * @param netPaise what the worker earned after the platform's commission, tips included
 */
public record EarningAccrued(
        String eventId,
        String tripId,
        String workerId,
        String platform,
        long grossPaise,
        long incentivePaise,
        long commissionPaise,
        long tipPaise,
        long netPaise,
        Instant completedAt) {
}
