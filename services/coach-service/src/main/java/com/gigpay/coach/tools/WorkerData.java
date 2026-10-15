package com.gigpay.coach.tools;

import com.gigpay.coach.config.AppProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * The worker's own data, fetched live from earnings-service and ledger-service. The coach only
 * ever asks about the signed-in worker: the worker id comes from the verified token, never from
 * the model, so a prompt like "show me Suresh's earnings" can't reach anyone else's data.
 */
@Component
public class WorkerData {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {
    };

    private final RestClient earnings;
    private final RestClient ledger;

    public WorkerData(RestClient.Builder builder, AppProperties props) {
        JdkClientHttpRequestFactory rf = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(2)).build());
        rf.setReadTimeout(Duration.ofSeconds(4));
        this.earnings = builder.clone().baseUrl(props.earningsUrl()).requestFactory(rf).build();
        this.ledger = builder.clone().baseUrl(props.ledgerUrl()).requestFactory(rf).build();
    }

    public Map<String, Object> profile(String workerId) {
        return earnings.get().uri("/internal/workers/{id}", workerId).retrieve().body(MAP);
    }

    public Map<String, Object> summary(String workerId, String period) {
        return earnings.get().uri("/internal/workers/{id}/summary?period={p}", workerId, period).retrieve().body(MAP);
    }

    public List<Map<String, Object>> trips(String workerId, int limit, String order) {
        return earnings.get().uri("/internal/workers/{id}/trips?limit={l}&order={o}", workerId, limit, order).retrieve().body(LIST);
    }

    public List<Map<String, Object>> incentives(String workerId) {
        return earnings.get().uri("/internal/workers/{id}/incentives", workerId).retrieve().body(LIST);
    }

    public List<Map<String, Object>> daily(String workerId) {
        return earnings.get().uri("/internal/workers/{id}/daily", workerId).retrieve().body(LIST);
    }

    public Map<String, Object> wallet(String workerId) {
        return ledger.get().uri("/api/wallet").header("X-User-Id", workerId).retrieve().body(MAP);
    }

    public List<Map<String, Object>> payouts(String workerId) {
        return ledger.get().uri("/api/wallet/payouts").header("X-User-Id", workerId).retrieve().body(LIST);
    }
}
