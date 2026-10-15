package com.gigpay.ledger.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Earned-wage-access policy and integration settings. All money in paise.
 *
 * @param withdrawableShare     share of earned balance a worker may take early; the rest is a buffer
 *                              for platform clawbacks (cancelled orders, penalties)
 * @param minPayout             smallest instant payout
 * @param dailyInstantLimit     total instant payouts per worker per day
 * @param dailyInstantCount     number of instant payouts per worker per day
 * @param instantFee            flat fee per instant payout (weekly payouts are free)
 * @param providerUrl           payout provider base URL (the built-in mock by default)
 * @param providerSecret        shared secret for signing provider webhooks
 * @param seedCapital           float the business starts with
 */
@ConfigurationProperties(prefix = "gigpay")
public record LedgerProperties(@DefaultValue("0.9") double withdrawableShare,
                               @DefaultValue("10000") long minPayout,
                               @DefaultValue("500000") long dailyInstantLimit,
                               @DefaultValue("3") int dailyInstantCount,
                               @DefaultValue("500") long instantFee,
                               @DefaultValue("http://localhost:8102") String providerUrl,
                               @DefaultValue("dev-provider-webhook-secret") String providerSecret,
                               @DefaultValue("100000000") long seedCapital,
                               @DefaultValue("120s") java.time.Duration settlementEvery,
                               @DefaultValue("6h") java.time.Duration weeklyPayoutEvery) {
}
