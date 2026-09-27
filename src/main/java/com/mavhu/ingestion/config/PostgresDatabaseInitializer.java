package com.mavhu.ingestion.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class PostgresDatabaseInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Pattern JDBC_URL_PATTERN =
            Pattern.compile("jdbc:postgresql://([^/]+)/([^?]+)");

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        Environment environment = applicationContext.getEnvironment();

        if (!isEnabled(environment)) {
            return;
        }

        String jdbcUrl = environment.getProperty("spring.datasource.url");
        String username = environment.getProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password");

        if (jdbcUrl == null || username == null || password == null) {
            log.warn("Skipping PostgreSQL database initialization because datasource properties are incomplete");
            return;
        }

        Matcher matcher = JDBC_URL_PATTERN.matcher(jdbcUrl);
        if (!matcher.matches()) {
            log.warn("Skipping PostgreSQL database initialization because datasource URL is not PostgreSQL: {}", jdbcUrl);
            return;
        }

        String hostAndPort = matcher.group(1);
        String databaseName = matcher.group(2);
        String adminJdbcUrl = "jdbc:postgresql://" + hostAndPort + "/postgres";

    }

    private boolean isEnabled(Environment environment) {
        if (!Arrays.asList(environment.getActiveProfiles()).contains("local")) {
            return false;
        }

        return environment.getProperty("app.datasource.initialize-database", Boolean.class, true);
    }

    private boolean databaseExists(Connection connection, String databaseName) throws SQLException {
        String sql = "SELECT 1 FROM pg_database WHERE datname = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, databaseName);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }
}
