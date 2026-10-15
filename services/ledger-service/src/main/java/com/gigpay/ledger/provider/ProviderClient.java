package com.gigpay.ledger.provider;

import com.gigpay.ledger.config.LedgerProperties;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/** HTTP client for the payout provider (a bank's payout API, such as RazorpayX or Cashfree Payouts). */
@Component
public class ProviderClient {

    private final RestClient rest;

    public ProviderClient(RestClient.Builder builder, LedgerProperties props) {
        JdkClientHttpRequestFactory rf = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(2)).build());
        rf.setReadTimeout(Duration.ofSeconds(4));
        this.rest = builder.baseUrl(props.providerUrl()).requestFactory(rf).build();
    }

    /** Our payout id is the provider's idempotency reference: a retried submit can never pay twice. */
    public Submission transfer(String reference, long amount, String upiId) {
        try {
            Map<?, ?> body = rest.post().uri("/mock-provider/transfers")
                    .body(Map.of("reference", reference, "amount", amount, "upiId", upiId))
                    .retrieve().body(Map.class);
            return new Submission(Outcome.ACCEPTED, String.valueOf(body.get("providerRef")), null);
        } catch (HttpClientErrorException e) {
            boolean retriable = e.getStatusCode().value() == 429;
            return new Submission(retriable ? Outcome.REJECTED_RETRIABLE : Outcome.REJECTED_PERMANENT, null,
                    e.getResponseBodyAsString());
        } catch (RuntimeException e) {
            return new Submission(Outcome.UNREACHABLE, null, "Provider unreachable: " + e.getClass().getSimpleName());
        }
    }

    public Status status(String reference) {
        try {
            return rest.get().uri("/mock-provider/transfers/{r}", reference).retrieve().body(Status.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public enum Outcome { ACCEPTED, REJECTED_RETRIABLE, REJECTED_PERMANENT, UNREACHABLE }

    public record Submission(Outcome outcome, String providerRef, String reason) {
    }

    public record Status(String reference, String providerRef, String status, boolean retriable, String reason, long amount) {
    }
}
