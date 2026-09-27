package com.mavhu.ingestion.users;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Read-only lookups on mavhu.users / user_roles / roles (owned by the platform schema, not this service).
 */
@Repository
@RequiredArgsConstructor
public class UserDirectory {

    public static final String MANAGER_ROLE = "MANAGER";

    private final JdbcTemplate jdbcTemplate;

    /** Active (not deleted) user by id. */
    public Optional<AppUser> findActiveUser(Integer userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                        SELECT id, customer_id, name, email FROM mavhu.users
                        WHERE id = ? AND is_deleted = FALSE""",
                (rs, rowNum) -> new AppUser(rs.getInt("id"), rs.getInt("customer_id"),
                        rs.getString("name"), rs.getString("email")),
                userId).stream().findFirst();
    }

    public boolean hasRole(Integer userId, String roleName) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM mavhu.user_roles ur
                        JOIN mavhu.roles r ON r.id = ur.role_id
                        WHERE ur.user_id = ? AND r.name = ?""",
                Integer.class, userId, roleName);
        return count != null && count > 0;
    }
}
