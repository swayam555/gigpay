package com.gigpay.earnings.auth;

import com.gigpay.earnings.worker.WorkerRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Worker sign-in with mobile number and a 4-digit PIN (hashed with BCrypt), as on most payout
 * apps. Five wrong PINs lock the number for 15 minutes. Tokens are verified by the gateway.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final int MAX_ATTEMPTS = 5;

    private final WorkerRepository workers;
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
    private final SecretKey key;
    private final String adminUser;
    private final String adminPassword;
    private final Map<String, Attempts> failures = new ConcurrentHashMap<>();

    public AuthController(WorkerRepository workers, @Value("${gigpay.jwt.secret}") String secret,
                          @Value("${gigpay.admin.username:finance}") String adminUser,
                          @Value("${gigpay.admin.password:gigpay}") String adminPassword) {
        this.workers = workers;
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.adminUser = adminUser;
        this.adminPassword = adminPassword;
    }

    @PostMapping("/worker")
    public ResponseEntity<Map<String, Object>> worker(@RequestBody WorkerLogin req) {
        String phone = req.phone() == null ? "" : req.phone().trim();
        Attempts a = failures.get(phone);
        if (a != null && a.count >= MAX_ATTEMPTS && a.until.isAfter(Instant.now())) {
            return ResponseEntity.status(429).body(Map.of("error", "Too many wrong PINs. Try again in 15 minutes."));
        }
        var hash = workers.pinHash(phone);
        if (hash.isEmpty() || hash.get() == null || req.pin() == null || !bcrypt.matches(req.pin(), hash.get())) {
            failures.merge(phone, new Attempts(1, Instant.now().plus(Duration.ofMinutes(15))),
                    (old, x) -> new Attempts(old.count + 1, Instant.now().plus(Duration.ofMinutes(15))));
            return ResponseEntity.status(401).body(Map.of("error", "Wrong mobile number or PIN"));
        }
        failures.remove(phone);
        var w = workers.byPhone(phone).orElseThrow();
        return ResponseEntity.ok(Map.of("accessToken", token(w.id(), w.name(), "WORKER", Duration.ofDays(30)),
                "userId", w.id(), "name", w.name(), "role", "WORKER"));
    }

    @PostMapping("/admin")
    public ResponseEntity<Map<String, Object>> admin(@RequestBody AdminLogin req) {
        if (!adminUser.equals(req.username()) || !adminPassword.equals(req.password())) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid username or password"));
        }
        return ResponseEntity.ok(Map.of("accessToken", token("admin-" + adminUser, "Finance team", "ADMIN", Duration.ofHours(12)),
                "userId", "admin-" + adminUser, "name", "Finance team", "role", "ADMIN"));
    }

    private String token(String sub, String name, String role, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder().subject(sub).claim("name", name).claim("role", role).issuer("gigpay")
                .issuedAt(Date.from(now)).expiration(Date.from(now.plus(ttl))).signWith(key).compact();
    }

    record Attempts(int count, Instant until) {
    }

    public record WorkerLogin(String phone, String pin) {
    }

    public record AdminLogin(String username, String password) {
    }

    /** Sets the demo PIN (1234) for seeded workers, stored as a BCrypt hash. */
    @Component
    static class PinSeeder implements ApplicationRunner {
        private final WorkerRepository workers;

        PinSeeder(WorkerRepository workers) {
            this.workers = workers;
        }

        @Override
        public void run(ApplicationArguments args) {
            workers.setMissingPins(new BCryptPasswordEncoder().encode("1234"));
        }
    }
}
