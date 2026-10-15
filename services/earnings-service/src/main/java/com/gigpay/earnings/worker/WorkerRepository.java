package com.gigpay.earnings.worker;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Repository
public class WorkerRepository {

    private static final String COLUMNS = "id, name, phone, city, vehicle, upi_id, platforms, language, joined_on";
    private static final RowMapper<Worker> MAPPER = (rs, n) -> new Worker(rs.getString("id"), rs.getString("name"),
            rs.getString("phone"), rs.getString("city"), rs.getString("vehicle"), rs.getString("upi_id"),
            Arrays.asList(rs.getString("platforms").split(",")), rs.getString("language"),
            rs.getObject("joined_on", java.time.LocalDate.class));

    private final JdbcClient jdbc;

    public WorkerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Worker> find(String id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM workers WHERE id = ?").param(id).query(MAPPER).optional();
    }

    public Optional<Worker> byPhone(String phone) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM workers WHERE phone = ?").param(phone).query(MAPPER).optional();
    }

    public Optional<String> pinHash(String phone) {
        return jdbc.sql("SELECT pin_hash FROM workers WHERE phone = ?").param(phone).query(String.class).optional();
    }

    public List<Worker> all() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM workers ORDER BY id").query(MAPPER).list();
    }

    public int setMissingPins(String hash) {
        return jdbc.sql("UPDATE workers SET pin_hash = ? WHERE pin_hash IS NULL").param(hash).update();
    }
}
