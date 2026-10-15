package com.gigpay.earnings.trip;

import com.gigpay.earnings.config.JsonCodec;
import com.gigpay.earnings.pay.Platform;
import com.gigpay.earnings.pay.PlatformRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Trip-completed webhooks from partner platforms. Each request is signed with the platform's
 * secret (HMAC-SHA256 over timestamp + body); unsigned, tampered or replayed (older than 5 min)
 * requests are rejected before anything is parsed.
 */
@RestController
public class WebhookController {

    private final PlatformRepository platforms;
    private final TripService trips;
    private final JsonCodec json;

    public WebhookController(PlatformRepository platforms, TripService trips, JsonCodec json) {
        this.platforms = platforms;
        this.trips = trips;
        this.json = json;
    }

    @PostMapping("/webhooks/platforms/{platformId}/trips")
    public ResponseEntity<Map<String, Object>> tripCompleted(@PathVariable String platformId, @RequestBody String body,
                                                             @RequestHeader(value = "X-GigPay-Timestamp", required = false) String ts,
                                                             @RequestHeader(value = "X-GigPay-Signature", required = false) String sig) {
        Platform p = platforms.find(platformId).orElse(null);
        if (p == null) return ResponseEntity.notFound().build();
        if (!Hmac.verify(p.webhookSecret(), ts, body, sig)) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid or expired signature"));
        }
        TripReport report = json.read(body, TripReport.class);
        try {
            boolean created = trips.record(p, report);
            return ResponseEntity.ok(Map.of("tripId", p.id() + ":" + report.externalId(), "duplicate", !created));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.unprocessableContent().body(Map.of("error", e.getMessage()));
        }
    }
}
