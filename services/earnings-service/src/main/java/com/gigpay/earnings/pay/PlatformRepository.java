package com.gigpay.earnings.pay;

import com.gigpay.earnings.config.JsonCodec;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class PlatformRepository {

    private static final TypeReference<List<Platform.StreakRule>> RULES = new TypeReference<>() {
    };
    private final JdbcClient jdbc;
    private final JsonCodec json;
    private final Map<String, Platform> cache = new ConcurrentHashMap<>();

    public PlatformRepository(JdbcClient jdbc, JsonCodec json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public Optional<Platform> find(String id) {
        if (cache.isEmpty()) all();
        return Optional.ofNullable(cache.get(id));
    }

    public List<Platform> all() {
        List<Platform> list = jdbc.sql("""
                        SELECT id, name, kind, webhook_secret, commission_bps, base_paise, per_km_paise, per_min_paise, streak_rules
                        FROM platforms ORDER BY name""")
                .query((rs, n) -> new Platform(rs.getString("id"), rs.getString("name"), rs.getString("kind"),
                        rs.getString("webhook_secret"), rs.getInt("commission_bps"), rs.getLong("base_paise"),
                        rs.getLong("per_km_paise"), rs.getLong("per_min_paise"), json.read(rs.getString("streak_rules"), RULES)))
                .list();
        list.forEach(p -> cache.put(p.id(), p));
        return list;
    }
}
