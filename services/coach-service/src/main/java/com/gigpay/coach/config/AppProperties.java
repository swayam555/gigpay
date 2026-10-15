package com.gigpay.coach.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "gigpay")
public record AppProperties(@DefaultValue("http://localhost:8101") String earningsUrl,
                            @DefaultValue("http://localhost:8102") String ledgerUrl,
                            Ai ai) {

    /**
     * @param provider {@code none} (rule-based coach), {@code openai} (any OpenAI-compatible API),
     *                 {@code anthropic} or {@code ollama}
     */
    public record Ai(@DefaultValue("none") String provider, String apiKey, String baseUrl, String model,
                     @DefaultValue("0.3") double temperature, @DefaultValue("60s") Duration timeout) {
    }
}
