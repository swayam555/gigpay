package com.gigpay.ledger.provider;

import com.gigpay.ledger.config.JsonCodec;
import com.gigpay.ledger.config.LedgerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A stand-in for the bank's payout API, with its own books (provider_transfers) and realistic
 * misbehaviour that can be dialled up from the finance console:
 * transient failures (NPCI timeouts), permanent failures (invalid UPI IDs), latency, and
 * dropped or duplicated webhooks. In production this is an external HTTP service; here it lives
 * in the same deployable for a self-contained demo, but GigPay only talks to it over HTTP.
 */
@RestController
public class MockPayoutProvider {

    private static final Logger log = LoggerFactory.getLogger(MockPayoutProvider.class);

    private final JdbcClient jdbc;
    private final JsonCodec json;
    private final LedgerProperties props;
    private final RestClient callbacks;
    private final ProviderFaults faults;

    public MockPayoutProvider(JdbcClient jdbc, JsonCodec json, LedgerProperties props, RestClient.Builder builder,
                              ProviderFaults faults) {
        this.jdbc = jdbc;
        this.json = json;
        this.props = props;
        this.faults = faults;
        JdkClientHttpRequestFactory rf = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(2)).build());
        this.callbacks = builder.baseUrl(props.providerUrl()).requestFactory(rf).build();
    }

    @PostMapping("/mock-provider/transfers")
    public ResponseEntity<Map<String, Object>> transfer(@RequestBody Map<String, Object> req) {
        String reference = String.valueOf(req.get("reference"));
        long amount = ((Number) req.get("amount")).longValue();
        String upi = String.valueOf(req.get("upiId"));
        var existing = jdbc.sql("SELECT provider_ref FROM provider_transfers WHERE reference = ?").param(reference)
                .query(String.class).optional();
        if (existing.isPresent()) {
            return ResponseEntity.ok(Map.of("providerRef", existing.get(), "status", "PENDING", "duplicate", true));
        }
        if (ThreadLocalRandom.current().nextDouble() < faults.current().rateLimitRate()) {
            return ResponseEntity.status(429).body(Map.of("error", "RATE_LIMITED"));
        }
        String providerRef = "UTR" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        long delayMs = 800 + ThreadLocalRandom.current().nextLong(Math.max(1, faults.current().settleMillis()));
        jdbc.sql("""
                        INSERT INTO provider_transfers (reference, provider_ref, amount, upi_id, status, settles_at)
                        VALUES (?, ?, ?, ?, 'PENDING', ?)""")
                .params(reference, providerRef, amount, upi, Timestamp.from(Instant.now().plusMillis(delayMs))).update();
        return ResponseEntity.ok(Map.of("providerRef", providerRef, "status", "PENDING"));
    }

    @GetMapping("/mock-provider/transfers/{reference}")
    public ResponseEntity<ProviderClient.Status> status(@PathVariable String reference) {
        return jdbc.sql("SELECT reference, provider_ref, status, reason, amount FROM provider_transfers WHERE reference = ?")
                .param(reference)
                .query((rs, n) -> new ProviderClient.Status(rs.getString("reference"), rs.getString("provider_ref"),
                        rs.getString("status"), "NPCI_TIMEOUT".equals(rs.getString("reason")), rs.getString("reason"),
                        rs.getLong("amount")))
                .optional().map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /** Settles due transfers and fires webhooks (some of which may be "lost" or sent twice). */
    @Scheduled(fixedDelay = 300)
    public void settle() {
        List<Map<String, Object>> due = jdbc.sql("""
                        SELECT reference, provider_ref, amount, upi_id FROM provider_transfers
                        WHERE status = 'PENDING' AND settles_at <= now() LIMIT 100""").query().listOfRows();
        for (Map<String, Object> t : due) {
            String upi = String.valueOf(t.get("upi_id"));
            String status = "SUCCESS";
            String reason = null;
            if (upi.toLowerCase().contains("invalid")) {
                status = "FAILED";
                reason = "INVALID_VPA";
            } else if (ThreadLocalRandom.current().nextDouble() < faults.current().failureRate()) {
                status = "FAILED";
                reason = "NPCI_TIMEOUT";
            }
            int updated = jdbc.sql("UPDATE provider_transfers SET status = ?, reason = ? WHERE reference = ? AND status = 'PENDING'")
                    .params(status, reason, t.get("reference")).update();
            if (updated == 1 && ThreadLocalRandom.current().nextDouble() >= faults.current().dropCallbackRate()) {
                sendCallback(String.valueOf(t.get("reference")), String.valueOf(t.get("provider_ref")), status, reason);
                if (ThreadLocalRandom.current().nextDouble() < faults.current().duplicateCallbackRate()) {
                    sendCallback(String.valueOf(t.get("reference")), String.valueOf(t.get("provider_ref")), status, reason);
                }
            }
        }
    }

    private void sendCallback(String reference, String providerRef, String status, String reason) {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("reference", reference);
        payload.put("providerRef", providerRef);
        payload.put("status", status);
        payload.put("reason", reason);
        payload.put("retriable", "NPCI_TIMEOUT".equals(reason));
        String body = json.write(payload);
        String ts = String.valueOf(System.currentTimeMillis() / 1000);
        try {
            callbacks.post().uri("/webhooks/provider/payouts")
                    .header("X-Provider-Timestamp", ts)
                    .header("X-Provider-Signature", Hmac.sign(props.providerSecret(), ts, body))
                    .header("Content-Type", "application/json")
                    .body(body).retrieve().toBodilessEntity();
            jdbc.sql("UPDATE provider_transfers SET callback_sent = TRUE WHERE reference = ?").param(reference).update();
        } catch (RuntimeException e) {
            log.debug("Callback for {} failed: {}", reference, e.getMessage());
        }
    }
}
