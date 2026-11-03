package com.ticketshop.admin;

import com.ticketshop.admin.authentication.application.*;
import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.*;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AuthenticationIT.TimeConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthenticationIT {
    @Container static MySQLContainer<?> mysql=new MySQLContainer<>("mysql:8.0").withDatabaseName("ticket_shop_admin_auth_test").withUrlParam("connectionTimeZone","UTC").withUrlParam("forceConnectionTimeZoneToSession","true");
    @Container static GenericContainer<?> redisContainer=new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    @DynamicPropertySource static void props(DynamicPropertyRegistry p) {
        p.add("ticket-shop.auth.allowed-origins",()->"http://127.0.0.1:8848,http://127.0.0.1:8849");
        p.add("spring.flyway.enabled",()->true);p.add("spring.datasource.url",mysql::getJdbcUrl);p.add("spring.datasource.username",mysql::getUsername);p.add("spring.datasource.password",mysql::getPassword);
        p.add("spring.data.redis.host",redisContainer::getHost);p.add("spring.data.redis.port",()->redisContainer.getMappedPort(6379));
    }
    static class MutableClock extends Clock {
        final AtomicReference<Instant> value=new AtomicReference<>(Instant.parse("2026-11-03T00:00:00Z"));
        public ZoneId getZone(){return ZoneOffset.UTC;} public Clock withZone(ZoneId z){return this;} public Instant instant(){return value.get();}
        void advance(long seconds){value.updateAndGet(i->i.plusSeconds(seconds));}
    }
    @TestConfiguration static class TimeConfig { @Bean @Primary MutableClock testClock(){return new MutableClock();} }
    @Autowired TestRestTemplate http; @Autowired JdbcTemplate jdbc; @Autowired AuthService auth; @Autowired Credentials credentials; @Autowired MutableClock clock; @Autowired ObjectMapper json; @Autowired StringRedisTemplate redis;
    static final String INITIAL="SyntheticInitialPassword-2026",NEW="SyntheticReplacementPassword-2026",THIRD="SyntheticThirdPassword-2026";
    long admin;
    @BeforeAll static void runtimeDirectory() throws Exception {
        var root=java.nio.file.Path.of("").toAbsolutePath();if(root.getFileName().toString().equals("backend"))root=root.getParent();java.nio.file.Files.createDirectories(root.resolve("runtime"));
    }
    @BeforeEach void fixtures() {
        clock.value.set(Instant.parse("2026-11-03T00:00:00Z"));
        for(String table:List.of("mutation_request","login_throttle","login_security_event","audit_event","customer_session","admin_session","admin_pre_auth","customer_account")) jdbc.update("DELETE FROM "+table);
        jdbc.update("UPDATE login_policy SET updated_by=NULL,client_max_hours=24,version=0");jdbc.update("UPDATE admin_account SET created_by=NULL,updated_by=NULL");jdbc.update("DELETE FROM admin_account");jdbc.update("UPDATE platform_control SET initialized=FALSE,recovery_mode=FALSE,version=0");
        admin=auth.bootstrap("SyntheticAdmin","合成管理员",INITIAL,UUID.randomUUID().toString());
    }
    ResponseEntity<JsonNode> request(String method,String path,Object body,String cookie,String csrf,String bearer) {
        var headers=new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);headers.setOrigin("http://127.0.0.1:8848");
        if(cookie!=null)headers.set("Cookie",cookie);if(csrf!=null)headers.set("X-CSRF-Token",csrf);if(bearer!=null)headers.setBearerAuth(bearer);
        return http.exchange(path,HttpMethod.valueOf(method),new HttpEntity<>(body,headers),JsonNode.class);
    }
    JsonNode data(ResponseEntity<JsonNode> r){assertThat(r.getStatusCode().value()).withFailMessage("%s",r.getBody()).isEqualTo(200);return r.getBody().get("data");}
    String cookies(ResponseEntity<JsonNode> r) { return r.getHeaders().getOrEmpty("Set-Cookie").stream().filter(s->!s.contains("Max-Age=0")).map(s->s.split(";",2)[0]).collect(java.util.stream.Collectors.joining("; ")); }
    ResponseEntity<JsonNode> login(String password){var csrf=request("GET","/api/v1/admin/auth/csrf",null,null,null,null);return request("POST","/api/v1/admin/auth/login",Map.of("username","SYNTHETICADMIN","password",password),cookies(csrf),data(csrf).get("csrfToken").asText(),null);}
    void makeNormal(){var login=login(INITIAL);data(request("POST","/api/v1/admin/auth/first-password",Map.of("newPassword",NEW),cookies(login),data(login).get("csrfToken").asText(),null));}
    void customer(boolean first) {
        jdbc.update("INSERT INTO customer_account(id,username,username_key,display_name,password_hash,password_version,status,valid_from,valid_until,must_change_password,version,created_by,updated_by,created_at,updated_at) VALUES(9001,'SyntheticClient','syntheticclient','合成客户',?,0,'ACTIVE','2026-11-02','2027-11-03',?,0,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",credentials.encode(INITIAL),first,admin,admin);
    }
    ResponseEntity<JsonNode> clientLogin(String username,String password){return request("POST","/api/v1/client/login",Map.of("username",username,"password",password,"os","MACOS","clientVersion","0.1.0","protocolVersion",1),null,null,null);}
    String clientToken(String password){return data(clientLogin("SyntheticClient",password)).get("token").asText();}
    void code(ResponseEntity<JsonNode> r,int status,String code){assertThat(r.getStatusCode().value()).isEqualTo(status);assertThat(r.getBody().at("/error/code").asText()).isEqualTo(code);assertThat(r.getHeaders().getFirst("Cache-Control")).isEqualTo("no-store");assertThat(r.getBody().get("requestId").asText()).isEqualTo(r.getHeaders().getFirst("X-Request-Id"));}
    @Test @Order(1) void bootstrapIsOneTimeAndAuditIsAtomic() {
        assertThatThrownBy(()->auth.bootstrap("OtherAdmin","其他",INITIAL,"second")).isInstanceOf(AuthProblem.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_account",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action='BOOTSTRAP'",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM admin_account",String.class)).startsWith("{argon2id}$argon2id$");
    }
    @Test @Order(2) void adminFirstPasswordNormalPasswordAndLogoutCookieFlow() {
        var first=login(INITIAL);var initial=data(first);assertThat(initial.get("nextAction").asText()).isEqualTo("CHANGE_PASSWORD");assertThat(initial.has("token")).isFalse();String restricted=cookies(first),csrf=initial.get("csrfToken").asText();
        assertThat(first.getHeaders().getFirst("Set-Cookie")).contains("HttpOnly","SameSite=Strict","Path=/api/v1/admin");
        code(request("GET","/api/v1/admin/auth/me",null,restricted,null,null),403,"PASSWORD_CHANGE_REQUIRED");
        data(request("POST","/api/v1/admin/auth/first-password",Map.of("newPassword",NEW),restricted,csrf,null));
        code(request("POST","/api/v1/admin/auth/first-password",Map.of("newPassword",THIRD),restricted,csrf,null),401,"PASSWORD_CHANGE_TOKEN_INVALID");
        var normal=login(NEW);String cookie=cookies(normal),normalCsrf=data(normal).get("csrfToken").asText();
        assertThat(data(request("GET","/api/v1/admin/auth/me",null,cookie,null,null)).get("role").asText()).isEqualTo("SUPER_ADMIN");
        var rotated=request("GET","/api/v1/admin/auth/csrf",null,cookie,null,null);normalCsrf=data(rotated).get("csrfToken").asText();
        data(request("POST","/api/v1/admin/auth/password",Map.of("currentPassword",NEW,"newPassword",THIRD),cookie,normalCsrf,null));
        code(request("GET","/api/v1/admin/auth/me",null,cookie,null,null),401,"SESSION_REVOKED");
        var last=login(THIRD);cookie=cookies(last);normalCsrf=data(last).get("csrfToken").asText();
        data(request("POST","/api/v1/admin/auth/logout",null,cookie,normalCsrf,null));data(request("POST","/api/v1/admin/auth/logout",null,cookie,normalCsrf,null));
        code(request("GET","/api/v1/admin/auth/me",null,cookie,null,null),401,"SESSION_REVOKED");
    }
    @Test @Order(3) void clientFlowRestrictedTokenCacheRebuildAndFixedExpiry() {
        customer(true);String restricted=clientToken(INITIAL);
        code(request("GET","/api/v1/client/account",null,null,null,restricted),403,"PASSWORD_CHANGE_REQUIRED");
        data(request("POST","/api/v1/client/first-password",Map.of("newPassword",NEW),null,null,restricted));
        code(request("POST","/api/v1/client/first-password",Map.of("newPassword",THIRD),null,null,restricted),401,"PASSWORD_CHANGE_TOKEN_INVALID");
        String token=clientToken(NEW);var q=data(request("POST","/api/v1/client/validate",Map.of("clientVersion","0.1.0","protocolVersion",1),null,null,token));String expires=q.get("expiresAt").asText();
        redis.delete(redis.keys("ticket-shop-admin:test:session:CUSTOMER:*"));clock.advance(3600);
        assertThat(data(request("GET","/api/v1/client/account",null,null,null,token)).get("expiresAt").asText()).isEqualTo(expires);
        assertThat(redis.keys("ticket-shop-admin:test:session:CUSTOMER:*")).isNotEmpty();
        data(request("POST","/api/v1/client/password",Map.of("currentPassword",NEW,"newPassword",THIRD),null,null,token));
        code(request("GET","/api/v1/client/account",null,null,null,token),401,"SESSION_REVOKED");
        token=clientToken(THIRD);data(request("POST","/api/v1/client/logout",null,null,null,token));data(request("POST","/api/v1/client/logout",null,null,null,token));
    }
    @Test @Order(4) void wrongPasswordDoesNotRevealStatusAndCountersSurviveCacheLoss() {
        customer(false);jdbc.update("UPDATE customer_account SET status='FROZEN',valid_until='2026-11-02 12:00:00'");
        code(clientLogin("SyntheticClient",NEW),401,"INVALID_CREDENTIALS");
        var denied=clientLogin("SyntheticClient",INITIAL);code(denied,403,"ACCOUNT_FROZEN");assertThat(denied.getBody().at("/error/details/denialReasons").toString()).contains("ACCOUNT_EXPIRED");
        for(int i=0;i<4;i++)code(clientLogin("MissingUser",INITIAL),401,"INVALID_CREDENTIALS");
        redis.delete(redis.keys("ticket-shop-admin:test:login-throttle:*"));code(clientLogin("MissingUser",INITIAL),401,"INVALID_CREDENTIALS");code(clientLogin("missinguser",INITIAL),429,"LOGIN_RATE_LIMITED");
        assertThat(jdbc.queryForObject("SELECT MAX(failure_count) FROM login_throttle WHERE realm='CUSTOMER' AND dimension='ACCOUNT'",Integer.class)).isEqualTo(5);
    }
    @Test @Order(5) void csrfOriginAndIdentityDomainsAreStrict() {
        makeNormal();var login=login(NEW);String cookie=cookies(login),csrf=data(login).get("csrfToken").asText();
        code(request("POST","/api/v1/admin/auth/activity",null,cookie,null,null),403,"CSRF_INVALID");
        var headers=new HttpHeaders();headers.set("Cookie",cookie);headers.set("X-CSRF-Token",csrf);headers.setOrigin("https://attacker.invalid");
        code(http.exchange("/api/v1/admin/auth/activity",HttpMethod.POST,new HttpEntity<>(null,headers),JsonNode.class),403,"CSRF_INVALID");
        code(request("GET","/api/v1/client/account",null,null,null,cookie.split("=",2)[1]),401,"SESSION_INVALID");
        customer(false);String client=clientToken(INITIAL);code(request("GET","/api/v1/admin/auth/me",null,"tsa_admin_session="+client,null,null),401,"SESSION_INVALID");
        String tampered=client.substring(0,client.length()-8)+"AAAAAAAA";code(request("GET","/api/v1/client/account",null,null,null,tampered),401,"SESSION_INVALID");
        code(http.getForEntity("/api/v1/admin/auth/csrf",JsonNode.class),403,"CSRF_INVALID");
    }
    @Test @Order(6) void clockBoundariesIdleAbsoluteAndRestrictedExpiry() {
        var restricted=login(INITIAL);clock.advance(600);code(request("GET","/api/v1/admin/auth/me",null,cookies(restricted),null,null),401,"SESSION_EXPIRED");
        clock.value.set(Instant.parse("2026-11-03T00:00:00Z"));makeNormal();var normal=login(NEW);String cookie=cookies(normal),csrf=data(normal).get("csrfToken").asText();
        clock.advance(1799);data(request("GET","/api/v1/admin/auth/me",null,cookie,null,null));clock.advance(1);code(request("GET","/api/v1/admin/auth/me",null,cookie,null,null),401,"SESSION_EXPIRED");
        normal=login(NEW);cookie=cookies(normal);csrf=data(normal).get("csrfToken").asText();
        for(int i=0;i<16;i++){clock.advance(1700);data(request("POST","/api/v1/admin/auth/activity",null,cookie,csrf,null));}
        clock.advance(1600);code(request("GET","/api/v1/admin/auth/me",null,cookie,null,null),401,"SESSION_EXPIRED");
        customer(false);String client=clientToken(INITIAL);clock.advance(86400);code(request("GET","/api/v1/client/account",null,null,null,client),401,"SESSION_EXPIRED");
    }
    @Test @Order(7) void staleCacheCannotOverrideDatabaseRevocationOrMaintenance() {
        customer(false);String token=clientToken(INITIAL);jdbc.update("UPDATE customer_session SET revoked_at=UTC_TIMESTAMP(6),revoked_reason='TEST' WHERE account_id=9001");
        code(request("GET","/api/v1/client/account",null,null,null,token),401,"SESSION_REVOKED");
        token=clientToken(INITIAL);jdbc.update("UPDATE platform_control SET recovery_mode=TRUE");code(request("GET","/api/v1/client/account",null,null,null,token),503,"MAINTENANCE");
    }
    @Test @Order(8) void longUnicodePasswordsAreNotTruncatedAndUnknownJsonIsRejected() {
        String longPassword="汉🙂".repeat(64);String hash=credentials.encode(longPassword);assertThat(credentials.matches(longPassword,hash)).isTrue();assertThat(credentials.matches("汉🙂".repeat(63)+"汉🙃",hash)).isFalse();
        assertThatThrownBy(()->credentials.encode(longPassword+"a")).isInstanceOf(AuthProblem.class);
        code(request("POST","/api/v1/admin/auth/login",Map.of("username","SyntheticAdmin","password",INITIAL,"role","SUPER_ADMIN"),null,null,null),400,"VALIDATION_ERROR");
        code(request("POST","/api/v1/client/login",Map.of("username","SyntheticClient","password",INITIAL,"os","MACOS","clientVersion","0.1.0","protocolVersion",2),null,null,null),426,"PROTOCOL_UNSUPPORTED");
    }
    @Test @Order(9) void concurrentFailuresStopAtFiveAndForwardedIpIsIgnored() throws Exception {
        customer(false);
        try(var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var start=new java.util.concurrent.CountDownLatch(1);var calls=new ArrayList<java.util.concurrent.Future<Integer>>();
            for(int i=0;i<10;i++)calls.add(executor.submit(()->{start.await();return clientLogin("SyntheticClient",NEW).getStatusCode().value();}));
            start.countDown();var results=new ArrayList<Integer>();for(var c:calls)results.add(c.get());
            assertThat(results).allMatch(i -> i==401 || i==429);
            assertThat(results.stream().filter(i->i==401).count()).isEqualTo(5);assertThat(results.stream().filter(i->i==429).count()).isEqualTo(5);
        }
        jdbc.update("UPDATE login_throttle SET failure_count=49,blocked_until=NULL WHERE realm='CUSTOMER' AND dimension='SOURCE'");
        code(clientLogin("AnotherUser",INITIAL),401,"INVALID_CREDENTIALS");
        var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);headers.set("X-Forwarded-For","203.0.113.1");
        code(http.exchange("/api/v1/client/login",HttpMethod.POST,new HttpEntity<>(Map.of("username","ThirdUser","password",INITIAL,"os","MACOS","clientVersion","0.1.0","protocolVersion",1),headers),JsonNode.class),429,"LOGIN_RATE_LIMITED");
    }
    @Test @Order(10) void auditFailureRollsBackPasswordAndRevocation() {
        var login=login(INITIAL);String cookie=cookies(login),csrf=data(login).get("csrfToken").asText();
        jdbc.execute("ALTER TABLE audit_event ADD CONSTRAINT ck_synthetic_audit CHECK (action <> 'FIRST_PASSWORD')");
        try {
            code(request("POST","/api/v1/admin/auth/first-password",Map.of("newPassword",NEW),cookie,csrf,null),503,"SERVICE_UNAVAILABLE");
            assertThat(jdbc.queryForObject("SELECT must_change_password FROM admin_account",Boolean.class)).isTrue();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_session WHERE revoked_at IS NOT NULL",Integer.class)).isZero();
        } finally { jdbc.execute("ALTER TABLE audit_event DROP CHECK ck_synthetic_audit"); }
        data(request("POST","/api/v1/admin/auth/first-password",Map.of("newPassword",NEW),cookie,csrf,null));
    }
    @Test @Order(11) void databaseFailureCannotBeHiddenByValidCache() {
        customer(false);String token=clientToken(INITIAL);mysql.getDockerClient().pauseContainerCmd(mysql.getContainerId()).exec();
        try { code(request("GET","/api/v1/client/account",null,null,null,token),503,"SERVICE_UNAVAILABLE"); }
        finally { mysql.getDockerClient().unpauseContainerCmd(mysql.getContainerId()).exec(); }
    }
    @Test @Order(12) void realOfflineCliUsesHiddenInputAndRejectsSecondInitialization() throws Exception {
        jdbc.update("DELETE FROM audit_event");jdbc.update("DELETE FROM admin_account");jdbc.update("UPDATE platform_control SET initialized=FALSE");
        java.nio.file.Path root=java.nio.file.Path.of("").toAbsolutePath();if(root.getFileName().toString().equals("backend"))root=root.getParent();
        var builder=new ProcessBuilder("python3",root.resolve("scripts/verify-bootstrap-cli.py").toString(),root.resolve("backend/target/ticket-shop-admin-0.1.0-SNAPSHOT.jar").toString(),System.getProperty("java.home")+"/bin/java");
        builder.environment().put("SPRING_PROFILES_ACTIVE","test");builder.environment().put("TICKET_SHOP_DB_URL",mysql.getJdbcUrl());builder.environment().put("TICKET_SHOP_DB_USERNAME",mysql.getUsername());builder.environment().put("TICKET_SHOP_DB_PASSWORD",mysql.getPassword());builder.environment().put("TICKET_SHOP_REDIS_HOST",redisContainer.getHost());builder.environment().put("TICKET_SHOP_REDIS_PORT",redisContainer.getMappedPort(6379).toString());
        builder.redirectErrorStream(true);builder.redirectOutput(root.resolve("runtime/p3-cli.log").toFile());var process=builder.start();
        try { assertThat(process.waitFor(70,java.util.concurrent.TimeUnit.SECONDS)).isTrue();assertThat(process.exitValue()).isZero(); }finally{if(process.isAlive())process.destroyForcibly();}
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_account",Integer.class)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT initialized FROM platform_control",Boolean.class)).isTrue();
    }
    @Test @Order(13) void realHttpProcessRestartDoesNotRenewClientSession() throws Exception {
        customer(false);
        var generator=java.security.KeyPairGenerator.getInstance("RSA");generator.initialize(2048);var pair=generator.generateKeyPair();
        java.nio.file.Path root=java.nio.file.Path.of("").toAbsolutePath();if(root.getFileName().toString().equals("backend"))root=root.getParent();
        int childPort;try(var socket=new java.net.ServerSocket(0)){childPort=socket.getLocalPort();}
        var builder=new ProcessBuilder(System.getProperty("java.home")+"/bin/java","-jar",root.resolve("backend/target/ticket-shop-admin-0.1.0-SNAPSHOT.jar").toString());
        var env=builder.environment();env.put("SPRING_PROFILES_ACTIVE","test");env.put("TICKET_SHOP_DB_URL",mysql.getJdbcUrl());env.put("TICKET_SHOP_DB_USERNAME",mysql.getUsername());env.put("TICKET_SHOP_DB_PASSWORD",mysql.getPassword());env.put("TICKET_SHOP_REDIS_HOST",redisContainer.getHost());env.put("TICKET_SHOP_REDIS_PORT",redisContainer.getMappedPort(6379).toString());env.put("TICKET_SHOP_SERVER_PORT",Integer.toString(childPort));
        env.put("TICKET_SHOP_JWT_PRIVATE_KEY",Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));env.put("TICKET_SHOP_JWT_PUBLIC_KEY",Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));env.put("TICKET_SHOP_HMAC_KEY",Base64.getEncoder().encodeToString(Credentials.hash(Credentials.random())));
        builder.redirectErrorStream(true);builder.redirectOutput(ProcessBuilder.Redirect.appendTo(root.resolve("runtime/p3-restart.log").toFile()));
        String token=null,expiry=null,authenticated=null;var client=new TestRestTemplate();
        for(int pass=0;pass<2;pass++) {
            var process=builder.start();
            try {
                var probe=java.net.http.HttpClient.newBuilder().version(java.net.http.HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(1)).build();boolean ready=false;
                for(int attempt=0;attempt<60;attempt++) {try {if(probe.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+childPort+"/actuator/health/readiness")).timeout(Duration.ofSeconds(1)).GET().build(),java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode()==200){ready=true;break;}}catch(Exception ignored){}Thread.sleep(100);}
                assertThat(ready).isTrue();String base="http://127.0.0.1:"+childPort;
                if(pass==0) {
                    var login=client.postForEntity(base+"/api/v1/client/login",Map.of("username","SyntheticClient","password",INITIAL,"os","MACOS","clientVersion","0.1.0","protocolVersion",1),JsonNode.class);
                    JsonNode d=data(login);token=d.get("token").asText();expiry=d.at("/qualification/expiresAt").asText();authenticated=d.at("/qualification/authenticatedAt").asText();
                } else {
                    var headers=new HttpHeaders();headers.setBearerAuth(token);var q=data(client.exchange(base+"/api/v1/client/account",HttpMethod.GET,new HttpEntity<>(null,headers),JsonNode.class));
                    assertThat(q.get("expiresAt").asText()).isEqualTo(expiry);assertThat(q.get("authenticatedAt").asText()).isEqualTo(authenticated);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_session",Integer.class)).isEqualTo(1);
                }
            } finally { process.destroy();if(!process.waitFor(10,java.util.concurrent.TimeUnit.SECONDS))process.destroyForcibly(); }
            if(pass==0)redis.delete(redis.keys("ticket-shop-admin:test:session:CUSTOMER:*"));
        }
    }
    @org.springframework.boot.test.web.server.LocalServerPort int port;
    @Test @Order(90)
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="P3_BROWSER",matches="true")
    void realBrowserFlowAgainstIsolatedDatabase() throws Exception {
        java.nio.file.Path root=java.nio.file.Path.of("").toAbsolutePath(); if(root.getFileName().toString().equals("backend")) root=root.getParent();
        var front=root.resolve("frontend").toFile(); var log=root.resolve("runtime/p3-browser-vite.log").toFile();
        var vite=new ProcessBuilder("corepack","pnpm@11.25.0","exec","vite","--host","127.0.0.1","--port","8849","--strictPort").directory(front).redirectErrorStream(true).redirectOutput(log);
        vite.environment().put("TICKET_SHOP_DEV_API_TARGET","http://127.0.0.1:"+port);
        var server=vite.start();
        try {
            var probe=java.net.http.HttpClient.newBuilder().version(java.net.http.HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(1)).build(); boolean ready=false;
            for(int i=0;i<50;i++){ try { if(probe.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:8849")).timeout(Duration.ofSeconds(1)).GET().build(),java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode()==200){ready=true;break;} } catch(Exception ignored){} Thread.sleep(100); }
            assertThat(ready).isTrue();
            var runner=new ProcessBuilder("corepack","pnpm@11.25.0","exec","playwright","test","authentication.spec.ts").directory(front).redirectErrorStream(true).redirectOutput(root.resolve("runtime/p3-browser.log").toFile());
            runner.environment().put("PLAYWRIGHT_BASE_URL","http://127.0.0.1:8849");runner.environment().put("PLAYWRIGHT_EXTERNAL_SERVER","true");runner.environment().put("P3_BROWSER","true");
            var browser=runner.start(); try { assertThat(browser.waitFor(120,java.util.concurrent.TimeUnit.SECONDS)).isTrue(); assertThat(browser.exitValue()).isZero(); } finally { if(browser.isAlive())browser.destroyForcibly(); }
        } finally { server.descendants().forEach(ProcessHandle::destroy);server.destroy();server.waitFor(5,java.util.concurrent.TimeUnit.SECONDS); }
    }
    @Test @Order(99) void redisFailureDoesNotIssueSessionOrAuthorize() {
        customer(false);String token=clientToken(INITIAL);
        var context=auth.authenticate(com.ticketshop.admin.authentication.infrastructure.AuthenticationMapper.Realm.CUSTOMER,token,"NORMAL",false);
        redisContainer.stop();
        assertThat(auth.changePassword(context,INITIAL,NEW,false,"synthetic-cache-pending").get("cacheSyncStatus")).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_session WHERE revoked_at IS NOT NULL",Integer.class)).isEqualTo(1);
        code(request("GET","/api/v1/client/account",null,null,null,token),503,"SERVICE_UNAVAILABLE");
        code(clientLogin("SyntheticClient",INITIAL),503,"SERVICE_UNAVAILABLE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_session",Integer.class)).isEqualTo(1);
    }
}
