package com.ticketshop.admin;

import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FoundationIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("ticket_shop_admin_test")
            .withUrlParam("connectionTimeZone", "UTC")
            .withUrlParam("forceConnectionTimeZoneToSession", "true");
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;

    @Test @Order(1)
    void readinessHasNoConnectionDetailsAndBusinessApiIsClosed() {
        var readiness = http.getForEntity("/actuator/health/readiness", String.class);
        assertThat(readiness.getStatusCode().value()).isEqualTo(200);
        assertThat(readiness.getBody()).isEqualTo("{\"status\":\"UP\"}");
        assertThat(http.getForEntity("/api/v1/admin/users", String.class).getStatusCode().value()).isEqualTo(401);
        assertThat(http.getForEntity("/actuator/health", String.class).getStatusCode().value()).isEqualTo(401);
        assertThat(http.postForEntity("/api/v1/admin/auth/login", "{}", String.class).getStatusCode().value()).isEqualTo(400);
    }

    @Test @Order(2)
    void mysqlEnforcesCheckAndUsesUtcWithMigratedSchema() throws SQLException {
        try (var connection = jdbc.getDataSource().getConnection(); var statement = connection.createStatement()) {
            assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(8);
            statement.execute("CREATE TEMPORARY TABLE foundation_check (value INT CHECK (value > 0))");
            assertThatThrownBy(() -> statement.execute("INSERT INTO foundation_check VALUES (0)"))
                    .isInstanceOf(SQLException.class);
            statement.execute("DROP TEMPORARY TABLE foundation_check");
        }
        assertThat(jdbc.queryForObject("SELECT @@session.time_zone", String.class)).isEqualTo("+00:00");
        assertThat(jdbc.queryForList("SHOW TABLES")).hasSize(12);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_account", Integer.class)).isZero();
    }

    @Test @Order(3)
    void redisRoundTripOnlyTouchesItsOwnUniqueKey() {
        String key = "ticket-shop-admin:test:foundation:" + UUID.randomUUID();
        try {
            redis.opsForValue().set(key, "probe", java.time.Duration.ofSeconds(30));
            assertThat(redis.opsForValue().get(key)).isEqualTo("probe");
            assertThat(redis.getExpire(key)).isBetween(1L, 30L);
        } finally {
            redis.delete(key);
        }
        assertThat(redis.hasKey(key)).isFalse();
    }

    @Autowired com.ticketshop.admin.customer.infrastructure.CustomerAccountMapper customers;
    @Autowired com.ticketshop.admin.session.infrastructure.CustomerSessionMapper sessions;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;

    private void createFixture(long id, String name, String start, String end) {
        jdbc.update("INSERT IGNORE INTO admin_account (id,username,username_key,display_name,password_hash,password_version,role,status,must_change_password,version,created_at,updated_at) VALUES (100,'SyntheticAdmin','syntheticadmin','合成管理员','not-a-real-hash',0,'SUPER_ADMIN','ACTIVE',true,0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        jdbc.update("INSERT INTO customer_account (id,username,username_key,display_name,password_hash,password_version,status,valid_from,valid_until,must_change_password,remark,version,created_by,updated_by,created_at,updated_at) VALUES (?, ?, LOWER(?),'合成用户','not-a-real-hash',0,'ACTIVE',?,?,true,'原始备注',0,100,100,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", id,name,name,start,end);
    }

    @Test @Order(10)
    void mapperPreservesMissingFieldsEnforcesVersionsAndSerializesOnlyDto() throws Exception {
        createFixture(1001,"CustomerOne","2026-11-01 00:00:00.000001","2026-12-01 00:00:00");
        var now = java.time.Instant.parse("2026-11-03T00:00:00Z");
        assertThat(customers.updateProfile(1001,"新的显示名",false,null,100,now,0)).isEqualTo(1);
        assertThat(customers.selectById(1001).getRemark()).isEqualTo("原始备注");
        assertThat(customers.updateProfile(1001,"过期覆盖",true,null,100,now,0)).isZero();
        assertThatThrownBy(() -> com.ticketshop.admin.common.api.ApiValues.requireChanged(0))
                .isInstanceOf(com.ticketshop.admin.common.api.ApiValues.VersionConflictException.class);
        assertThat(customers.updateProfile(1001,null,true,null,100,now,1)).isEqualTo(1);
        var account = customers.selectById(1001);
        assertThat(account.getRemark()).isNull();
        assertThat(account.getValidFrom()).isEqualTo(java.time.Instant.parse("2026-11-01T00:00:00.000001Z"));
        var view = com.ticketshop.admin.customer.api.CustomerView.from(account,now);
        String payload = json.writeValueAsString(view);
        assertThat(payload).contains("\"id\":\"1001\"", "\"version\":\"2\"").doesNotContain("password", "token", "not-a-real-hash");
        var newer = customers.selectById(1001);
        var stale = customers.selectById(1001);
        newer.setDisplayName("插件正确版本");
        assertThat(customers.updateById(newer)).isEqualTo(1);
        stale.setDisplayName("插件过期版本");
        assertThat(customers.updateById(stale)).isZero();
    }

    @Test @Order(11)
    void usernameValidityForeignKeysAndConcurrentCompareAndSetHold() throws Exception {
        createFixture(1002,"CustomerTwo","2026-11-03 00:00:00","2026-12-01 00:00:00");
        assertThatThrownBy(() -> createFixture(1003,"customertwo","2026-11-03 00:00:00","2026-12-01 00:00:00")).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThatThrownBy(() -> customers.updateValidity(1002,java.time.Instant.EPOCH,java.time.Instant.EPOCH,100,java.time.Instant.now(),0)).isInstanceOf(org.springframework.dao.DataAccessException.class).rootCause().isInstanceOf(java.sql.SQLException.class).extracting(e -> ((java.sql.SQLException)e).getErrorCode()).isEqualTo(3819);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM admin_account WHERE id=100")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Integer> update = () -> { start.await(); return customers.updateProfile(1002,"并发变更",false,null,100,java.time.Instant.now(),0); };
            var a = executor.submit(update); var b = executor.submit(update); start.countDown();
            assertThat(a.get()+b.get()).isEqualTo(1);
        }
    }

    @Test @Order(12)
    void queryUsesExclusiveBoundsAndLiteralKeywordsAndStablePagination() {
        createFixture(1004,"BoundaryStart","2026-11-03 00:00:00","2026-11-04 00:00:00");
        var start = java.time.Instant.parse("2026-11-03T00:00:00Z");
        var end = java.time.Instant.parse("2026-11-04T00:00:00Z");
        var sort = com.ticketshop.admin.customer.infrastructure.CustomerFilter.Sort.CREATED_AT_DESC;
        var filter = new com.ticketshop.admin.customer.infrastructure.CustomerFilter("Boundary",null,"IN_TERM",null,null,start,sort);
        assertThat(customers.search(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1,10),filter).getRecords()).hasSize(1);
        filter = new com.ticketshop.admin.customer.infrastructure.CustomerFilter("Boundary",null,"IN_TERM",null,null,end,sort);
        assertThat(customers.search(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1,10),filter).getRecords()).isEmpty();
        filter = new com.ticketshop.admin.customer.infrastructure.CustomerFilter("' OR 1=1 --",null,null,null,null,start,sort);
        assertThat(customers.search(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1,10),filter).getTotal()).isZero();
        assertThat(com.ticketshop.admin.customer.api.CustomerView.from(customers.selectById(1004),start).termStatus()).isEqualTo("IN_TERM");
        assertThat(com.ticketshop.admin.customer.api.CustomerView.from(customers.selectById(1004),end).termStatus()).isEqualTo("EXPIRED");
    }

    @Test @Order(13)
    void shorteningNeverExtendsOrRevivesRevokedSessions() {
        createFixture(1005,"SessionOwner","2026-11-03 00:00:00","2026-12-01 00:00:00");
        String sid = "a".repeat(43), jti = "b".repeat(43);
        jdbc.update("INSERT INTO customer_session (id,account_id,sid,jti,token_hash,version,purpose,authenticated_at,expires_at,last_validated_at,policy_version,os,client_version,protocol_version,created_at) VALUES (2001,1005,?,?,UNHEX(SHA2('synthetic-token',256)),0,'NORMAL','2026-11-03 00:00:00','2026-11-04 00:00:00','2026-11-03 00:00:00',0,'MACOS','0.1.0',1,'2026-11-03 00:00:00')",sid,jti);
        var now = java.time.Instant.parse("2026-11-03T01:00:00Z");
        assertThat(sessions.shortenUnexpired(1005,2,1,now)).isEqualTo(1);
        assertThat(sessions.shortenUnexpired(1005,24,2,now)).isZero();
        assertThat(sessions.selectById(2001).getExpiresAt()).isEqualTo(java.time.Instant.parse("2026-11-03T02:00:00Z"));
        assertThat(sessions.revokeUnrevoked(1005,"ADMIN_REVOKE",now)).isEqualTo(1);
        assertThat(sessions.revokeUnrevoked(1005,"ADMIN_REVOKE",now)).isZero();
        assertThat(sessions.shortenUnexpired(1005,1,3,now)).isZero();
        assertThat(sessions.selectById(2001).getRevokedAt()).isEqualTo(now);
    }

    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired com.ticketshop.admin.audit.infrastructure.AuditEventMapper audit;

    @Test @Order(14)
    void failedAuditRollsBackTheDataMutationInTheSameTransaction() {
        createFixture(1006,"AuditedCustomer","2026-11-03 00:00:00","2026-12-01 00:00:00");
        var event = new com.ticketshop.admin.audit.infrastructure.AuditEventEntity();
        event.setId(3001L); event.setActorType("ADMIN"); event.setActorId(100L); event.setActorNameSnapshot("合成管理员");
        event.setTargetType("CUSTOMER"); event.setTargetId(1006L); event.setAction("PROFILE_UPDATE");
        event.setResult("INVALID"); event.setRequestId("synthetic-audit"); event.setOccurredAt(java.time.Instant.now());
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactions);
        assertThatThrownBy(() -> transaction.execute(status -> {
            customers.updateProfile(1006,"应当回滚",false,null,100,java.time.Instant.now(),0);
            return audit.insert(event);
        })).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(customers.selectById(1006).getVersion()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE id=3001", Integer.class)).isZero();
        event.setResult("SUCCESS");
        transaction.executeWithoutResult(status -> {
            com.ticketshop.admin.common.api.ApiValues.requireChanged(customers.updateProfile(1006,"已提交",false,null,100,java.time.Instant.now(),0));
            audit.insert(event);
        });
        assertThat(customers.selectById(1006).getVersion()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE id=3001", Integer.class)).isEqualTo(1);
    }

    @Test @Order(99)
    void failedDependencyClosesReadinessButDoesNotKillLiveness() {
        REDIS.stop(); // This container belongs exclusively to this test class.
        assertThat(http.getForEntity("/actuator/health/readiness", String.class).getStatusCode().value()).isEqualTo(503);
        assertThat(http.getForEntity("/actuator/health/liveness", String.class).getStatusCode().value()).isEqualTo(200);
    }
}
