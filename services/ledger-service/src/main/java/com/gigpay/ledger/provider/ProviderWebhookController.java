package com.gigpay.ledger.provider;

import com.gigpay.ledger.config.JsonCodec;
import com.gigpay.ledger.config.LedgerProperties;
import com.gigpay.ledger.payout.PayoutService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Receives payout results from the provider. Unsigned or stale (replayed) requests are rejected. */
@RestController
public class ProviderWebhookController {

    private final PayoutService payouts;
    private final LedgerProperties props;
    private final JsonCodec json;

    public ProviderWebhookController(PayoutService payouts, LedgerProperties props, JsonCodec json) {
        this.payouts = payouts;
        this.props = props;
        this.json = json;
    }

    @PostMapping("/webhooks/provider/payouts")
    public ResponseEntity<Void> callback(@RequestBody String body,
                                         @RequestHeader(value = "X-Provider-Timestamp", required = false) String ts,
                                         @RequestHeader(value = "X-Provider-Signature", required = false) String sig) {
        if (!Hmac.verify(props.providerSecret(), ts, body, sig)) return ResponseEntity.status(401).build();
        @SuppressWarnings("unchecked")
        Map<String, Object> m = json.read(body, Map.class);
        payouts.onResult(String.valueOf(m.get("reference")), "SUCCESS".equals(m.get("status")),
                Boolean.TRUE.equals(m.get("retriable")), (String) m.get("reason"));
        return ResponseEntity.ok().build();
    }
}
