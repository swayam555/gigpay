package com.gigpay.gateway;

import io.jsonwebtoken.Claims;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Verifies the JWT at the edge and enforces roles: finance endpoints need the ADMIN role. Only
 * sign-in is public. Client-supplied X-User-* headers are always stripped, so nobody can claim
 * to be another worker and read or withdraw their money.
 */
@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {

    public static final String USER_ATTRIBUTE = "gigpay.user";
    private static final List<String> PUBLIC = List.of("/api/auth/", "/actuator/health");
    private static final List<String> OPS_ONLY = List.of("/api/ledger/admin/", "/api/earnings/admin/");

    private final Jwt jwt;

    public AuthenticationFilter(Jwt jwt) {
        this.jwt = jwt;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        ServerHttpRequest stripped = exchange.getRequest().mutate().headers(h -> {
            h.remove("X-User-Id");
            h.remove("X-User-Name");
            h.remove("X-User-Role");
        }).build();
        boolean isPublic = PUBLIC.stream().anyMatch(path::startsWith)
                && (exchange.getRequest().getMethod() == HttpMethod.POST || exchange.getRequest().getMethod() == HttpMethod.GET);
        if (!path.startsWith("/api/") || isPublic) {
            return chain.filter(exchange.mutate().request(stripped).build());
        }
        Optional<Claims> claims = token(exchange).flatMap(jwt::verify);
        if (claims.isEmpty()) return reject(exchange, HttpStatus.UNAUTHORIZED, "Missing or invalid token");
        Claims c = claims.get();
        String role = String.valueOf(c.get("role"));
        if (OPS_ONLY.stream().anyMatch(path::startsWith) && !"ADMIN".equals(role)) {
            return reject(exchange, HttpStatus.FORBIDDEN, "Finance role required");
        }
        ServerHttpRequest withIdentity = stripped.mutate()
                .header("X-User-Id", c.getSubject())
                .header("X-User-Name", String.valueOf(c.get("name")))
                .header("X-User-Role", role)
                .build();
        exchange.getAttributes().put(USER_ATTRIBUTE, c.getSubject());
        return chain.filter(exchange.mutate().request(withIdentity).build());
    }

    private static Optional<String> token(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) return Optional.of(header.substring(7));
        return Optional.ofNullable(exchange.getRequest().getQueryParams().getFirst("access_token"));
    }

    private static Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String message) {
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = ("{\"error\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
