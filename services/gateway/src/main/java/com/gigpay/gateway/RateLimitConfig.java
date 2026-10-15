package com.gigpay.gateway;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Optional;

@Configuration
public class RateLimitConfig {

    /** Rate limits are per authenticated user, falling back to client IP. */
    @Bean
    KeyResolver userKeyResolver() {
        return exchange -> {
            Object user = exchange.getAttributes().get(AuthenticationFilter.USER_ATTRIBUTE);
            if (user != null) return Mono.just("user:" + user);
            String ip = Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                    .map(InetSocketAddress::getHostString).orElse("unknown");
            return Mono.just("ip:" + ip);
        };
    }
}
