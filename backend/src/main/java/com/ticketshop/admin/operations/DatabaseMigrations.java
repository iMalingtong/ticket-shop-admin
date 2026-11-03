package com.ticketshop.admin.operations;

import java.net.URI;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;

/** Explicit migration entry point. No clean, repair, baseline, credential logging or automatic rollback. */
public final class DatabaseMigrations {
    private DatabaseMigrations() {}
    public static FluentConfiguration configure(DataSource source) {
        return Flyway.configure().dataSource(source).locations("classpath:db/migration")
                .cleanDisabled(true).baselineOnMigrate(false).outOfOrder(false)
                .initSql("SET time_zone = '+00:00'")
                .validateMigrationNaming(true).validateOnMigrate(true).ignoreMigrationPatterns(new String[0]);
    }
    public static void validateCurrent(DataSource source) {
        try (var connection = source.getConnection()) { requireMySql80(connection); }
        catch (java.sql.SQLException error) { throw new IllegalStateException("Database schema connection check failed", error); }
        var flyway = configure(source).load();
        flyway.validate();
        var current = flyway.info().current();
        if (current == null || !"3".equals(current.getVersion().getVersion()) || flyway.info().pending().length != 0) {
            throw new IllegalStateException("Database schema is not current; run the explicit migration entry point first");
        }
    }
    public static void run(Map<String, String> env) throws Exception {
        String profile = required(env, "SPRING_PROFILES_ACTIVE");
        if (!java.util.Set.of("development", "test", "production").contains(profile)) {
            throw new IllegalArgumentException("A single explicit runtime profile is required");
        }
        String url = required(env, "TICKET_SHOP_DB_URL");
        if (!url.startsWith("jdbc:mysql://")) throw new IllegalArgumentException("MySQL JDBC URL required");
        var target = URI.create(url.substring(5));
        if (target.getUserInfo() != null || url.matches("(?i).*([?&])(user|password)=.*")) {
            throw new IllegalArgumentException("Credentials must be supplied separately from the JDBC URL");
        }
        String database = target.getPath();
        if (database == null || !database.matches("/ticket_shop_admin(?:_[a-z0-9_]+)?")) {
            throw new IllegalArgumentException("Migration is restricted to the project database namespace");
        }
        var source = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                url, required(env, "TICKET_SHOP_MIGRATION_USERNAME"), required(env, "TICKET_SHOP_MIGRATION_PASSWORD"));
        try (var connection = source.getConnection()) {
            requireMySql80(connection);
        }
        configure(source).load().migrate();
        validateCurrent(source);
        System.out.println("Project database migration and validation completed at V3");
    }
    private static void requireMySql80(java.sql.Connection connection) throws java.sql.SQLException {
        var metadata = connection.getMetaData();
        if (metadata.getDatabaseMajorVersion() != 8 || metadata.getDatabaseMinorVersion() != 0
                || !metadata.getDatabaseProductVersion().matches("8\\.0\\.(?:1[6-9]|[2-9][0-9]|[1-9][0-9]{2,})(?:[-.].*)?")) {
            throw new IllegalStateException("MySQL 8.0.16 or newer 8.0 patch is required");
        }
    }
    private static String required(Map<String, String> env, String key) {
        String value = env.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing required setting: " + key);
        return value;
    }
}
