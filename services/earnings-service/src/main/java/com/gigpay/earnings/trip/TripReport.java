package com.gigpay.earnings.trip;

import java.time.Instant;

/** What a platform sends when a trip completes (the webhook payload). */
public record TripReport(String externalId, String workerId, double distanceKm, int durationMin, int waitMin,
                         double surge, long tipPaise, String pickupArea, String dropArea, Instant completedAt) {
}
