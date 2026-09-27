package com.mavhu.ingestion;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Seeds customers, users and roles into the H2 test database (tables created by Hibernate from the
 * entities). Idempotent, so every test class can call it.
 */
public final class TestUsers {

    public record Ids(int customerId, int managerId, int uploaderId, int auditorId,
                      int otherCustomerId, int otherManagerId) {
    }

    private TestUsers() {
    }

    public static Ids seed(JdbcTemplate jdbc) {
        for (String role : new String[]{"MANAGER", "ESG_TEAM", "AUDITOR"}) {
            jdbc.update("INSERT INTO mavhu.roles (name) SELECT ? WHERE NOT EXISTS (SELECT 1 FROM mavhu.roles WHERE name = ?)",
                    role, role);
        }
        int customer = customer(jdbc, "CBZ", "esg@cbz.example");
        int other = customer(jdbc, "Other Bank", "esg@other.example");
        return new Ids(customer,
                user(jdbc, customer, "Tendai Moyo", "tendai.moyo@cbz.example", "MANAGER"),
                user(jdbc, customer, "Tatenda Shumba", "tatenda.shumba@cbz.example", "ESG_TEAM"),
                user(jdbc, customer, "Tapiwa Dube", "tapiwa.dube@cbz.example", "AUDITOR"),
                other,
                user(jdbc, other, "Other Manager", "manager@other.example", "MANAGER"));
    }

    private static int customer(JdbcTemplate jdbc, String name, String email) {
        jdbc.update("""
                INSERT INTO mavhu.customers (name, hectares, country, email, identifier, created_at, updated_at, is_deleted)
                SELECT ?, 100, 'Zimbabwe', ?, 'BANK', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
                WHERE NOT EXISTS (SELECT 1 FROM mavhu.customers WHERE email = ?)""", name, email, email);
        return jdbc.queryForObject("SELECT id FROM mavhu.customers WHERE email = ?", Integer.class, email);
    }

    private static int user(JdbcTemplate jdbc, int customerId, String name, String email, String role) {
        jdbc.update("""
                INSERT INTO mavhu.users (name, customer_id, email, password, created_at, updated_at, is_deleted)
                SELECT ?, ?, ?, 'not-a-real-hash', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
                WHERE NOT EXISTS (SELECT 1 FROM mavhu.users WHERE email = ?)""", name, customerId, email, email);
        int userId = jdbc.queryForObject("SELECT id FROM mavhu.users WHERE email = ?", Integer.class, email);
        jdbc.update("""
                INSERT INTO mavhu.user_roles (user_id, role_id, created_at, updated_at)
                SELECT ?, r.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM mavhu.roles r
                WHERE r.name = ? AND NOT EXISTS (
                    SELECT 1 FROM mavhu.user_roles ur WHERE ur.user_id = ? AND ur.role_id = r.id)""",
                userId, role, userId);
        return userId;
    }
}
