package com.ticketshop.admin;

import java.nio.file.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import com.ticketshop.admin.operations.DatabaseMigrations;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class MigrationIT {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ticket_shop_admin_test").withUrlParam("connectionTimeZone", "UTC")
            .withUrlParam("forceConnectionTimeZoneToSession", "true");
    @TempDir Path changed;

    @Test void fullUpgradeRepeatConstraintsAndChecksumGuard() throws Exception {
        DataSource source = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(source);
        assertThat(DatabaseMigrations.configure(source).target("1").load().migrate().migrationsExecuted).isEqualTo(1);
        jdbc.update("INSERT INTO admin_account (id,username,username_key,display_name,password_hash,password_version,role,status,must_change_password,version,created_at,updated_at) VALUES (1,'SyntheticAdmin','syntheticadmin','合成管理员','not-a-real-hash',0,'SUPER_ADMIN','ACTIVE',true,0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        assertThat(DatabaseMigrations.configure(source).load().migrate().migrationsExecuted).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT display_name FROM admin_account WHERE id=1", String.class)).isEqualTo("合成管理员");
        assertThat(DatabaseMigrations.configure(source).load().migrate().migrationsExecuted).isZero();
        DatabaseMigrations.validateCurrent(source);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name <> 'flyway_schema_history'", Integer.class)).isEqualTo(11);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name <> 'flyway_schema_history' AND table_comment=''", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name <> 'flyway_schema_history' AND column_comment=''", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND delete_rule <> 'RESTRICT'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE()", Integer.class)).isGreaterThanOrEqualTo(7);
        assertThatThrownBy(() -> jdbc.update("UPDATE login_policy SET client_max_hours=25 WHERE id=1")).isInstanceOf(org.springframework.dao.DataAccessException.class).rootCause().isInstanceOf(java.sql.SQLException.class).extracting(e -> ((java.sql.SQLException)e).getErrorCode()).isEqualTo(3819);
        assertThatThrownBy(() -> jdbc.update("UPDATE admin_account SET role='ROOT' WHERE id=1")).isInstanceOf(org.springframework.dao.DataAccessException.class).rootCause().isInstanceOf(java.sql.SQLException.class).extracting(e -> ((java.sql.SQLException)e).getErrorCode()).isEqualTo(3819);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO platform_control VALUES (2,false,false,0,UTC_TIMESTAMP(6))")).isInstanceOf(org.springframework.dao.DataAccessException.class).rootCause().isInstanceOf(java.sql.SQLException.class).extracting(e -> ((java.sql.SQLException)e).getErrorCode()).isEqualTo(3819);
        jdbc.update("INSERT INTO login_throttle (id,realm,dimension,key_digest,failure_count,window_started_at,updated_at,version) VALUES (1,'CUSTOMER','ACCOUNT',UNHEX(SHA2('synthetic-account',256)),0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0)");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO login_throttle (id,realm,dimension,key_digest,failure_count,window_started_at,updated_at,version) SELECT 2,realm,dimension,key_digest,0,window_started_at,updated_at,0 FROM login_throttle WHERE id=1")).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        jdbc.update("INSERT INTO mutation_request (id,actor_type,actor_id,idempotency_key,route,fingerprint,state,request_id,created_at,expires_at) VALUES (1,'ADMIN',1,'00000000-0000-4000-8000-000000000001','POST /users',UNHEX(SHA2('synthetic-mutation',256)),'PROCESSING','synthetic-request',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)+INTERVAL 24 HOUR)");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO mutation_request (id,actor_type,actor_id,idempotency_key,route,fingerprint,state,request_id,created_at,expires_at) SELECT 2,actor_type,actor_id,idempotency_key,'POST /policy',fingerprint,state,request_id,created_at,expires_at FROM mutation_request WHERE id=1")).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT table_name,index_name) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name <> 'flyway_schema_history'", Integer.class)).isGreaterThan(30);
        for (String file : new String[]{"V1__accounts_and_control.sql", "V2__sessions_and_policy.sql", "V3__audit_throttle_idempotency.sql"}) {
            try (var input = getClass().getResourceAsStream("/db/migration/" + file)) { Files.copy(input, changed.resolve(file)); }
        }
        Files.writeString(changed.resolve("V1__accounts_and_control.sql"), "\n-- changed only in isolated test copy\n", StandardOpenOption.APPEND);
        Flyway tampered = DatabaseMigrations.configure(source).locations("filesystem:"+changed).load();
        assertThat(tampered.validateWithResult().validationSuccessful).isFalse();
        assertThatThrownBy(tampered::migrate).isInstanceOf(org.flywaydb.core.api.exception.FlywayValidateException.class);
        assertThatThrownBy(() -> DatabaseMigrations.configure(source).load().clean()).isInstanceOf(org.flywaydb.core.api.FlywayException.class);
    }
}
