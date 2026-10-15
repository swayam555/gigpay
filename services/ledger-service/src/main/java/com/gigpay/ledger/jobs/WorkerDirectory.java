package com.gigpay.ledger.jobs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Looks up a worker's registered payout UPI ID from earnings-service (the owner of worker profiles). */
@Component
public class WorkerDirectory {

    private final RestClient rest;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public WorkerDirectory(RestClient.Builder builder, @Value("${gigpay.earnings-url:http://localhost:8101}") String url) {
        JdkClientHttpRequestFactory rf = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(2)).build());
        rf.setReadTimeout(Duration.ofSeconds(3));
        this.rest = builder.baseUrl(url).requestFactory(rf).build();
    }

    public String payoutUpi(String workerId) {
        return cache.computeIfAbsent(workerId, id -> {
            Map<String, Object> w = rest.get().uri("/internal/workers/{id}", id).retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return w == null ? null : String.valueOf(w.get("upiId"));
        });
    }
}
