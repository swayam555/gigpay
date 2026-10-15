package com.gigpay.ledger.provider;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/** Chaos dials for the mock bank, set from the finance console. */
@Component
public class ProviderFaults {

    private final AtomicReference<Faults> current = new AtomicReference<>(new Faults(0.05, 0.05, 0.05, 0.02, 2500));

    public Faults current() {
        return current.get();
    }

    public Faults update(Faults f) {
        if (f.failureRate() < 0 || f.failureRate() > 1 || f.dropCallbackRate() < 0 || f.dropCallbackRate() > 1
                || f.duplicateCallbackRate() < 0 || f.duplicateCallbackRate() > 1 || f.rateLimitRate() < 0
                || f.rateLimitRate() > 1 || f.settleMillis() < 0 || f.settleMillis() > 60_000) {
            throw new IllegalArgumentException("Rates must be 0-1 and settle time 0-60000 ms");
        }
        current.set(f);
        return f;
    }

    /**
     * @param failureRate           share of transfers that fail with a transient NPCI timeout
     * @param dropCallbackRate      share of webhooks that never arrive
     * @param duplicateCallbackRate share of webhooks delivered twice
     * @param rateLimitRate         share of submissions rejected with HTTP 429
     * @param settleMillis          extra random settlement delay
     */
    public record Faults(double failureRate, double dropCallbackRate, double duplicateCallbackRate,
                         double rateLimitRate, long settleMillis) {
    }
}
