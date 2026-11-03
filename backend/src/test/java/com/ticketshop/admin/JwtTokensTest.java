package com.ticketshop.admin;

import com.ticketshop.admin.authentication.application.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jwt.*;
import java.security.*;
import java.security.interfaces.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class JwtTokensTest {
    private RSAPrivateKey privateKey; private MockEnvironment env; private JwtTokens tokens;
    private final Instant now=Instant.parse("2026-11-03T00:00:00Z");
    @BeforeEach void setup() throws Exception {
        var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);var pair=generator.generateKeyPair();privateKey=(RSAPrivateKey)pair.getPrivate();env=new MockEnvironment();env.setActiveProfiles("development");
        env.setProperty("ticket-shop.auth.private-key",Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));env.setProperty("ticket-shop.auth.public-key",Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));env.setProperty("ticket-shop.auth.hmac-key",Base64.getEncoder().encodeToString(new byte[32]));
        tokens=new JwtTokens(env,Clock.fixed(now,ZoneOffset.UTC));
    }
    @Test void newProcessConfigurationPreservesOriginalTokenDeadlineAndIdentityDomain() {
        String token=tokens.sign("CUSTOMER",123,Credentials.random(),Credentials.random(),"NORMAL",now,now.plusSeconds(86400));
        var restarted=new JwtTokens(env,Clock.fixed(now.plusSeconds(3600),ZoneOffset.UTC));
        assertThat(restarted.verify(token,"CUSTOMER",false).expires()).isEqualTo(now.plusSeconds(86400));
        assertThatThrownBy(()->restarted.verify(token,"ADMIN",false)).isInstanceOf(AuthProblem.class);
        env.setActiveProfiles("production");assertThatThrownBy(()->new JwtTokens(env,Clock.fixed(now,ZoneOffset.UTC)).verify(token,"CUSTOMER",false)).isInstanceOf(AuthProblem.class);
    }
    @Test void wrongAlgorithmKeyIdIssuerAndMissingClaimsAreRejected() throws Exception {
        var good=new JWTClaimsSet.Builder().issuer("urn:ticket-shop-admin:development").audience("ticket-shop-client").subject("123").jwtID(Credentials.random()).claim("sid",Credentials.random()).claim("principalType","CUSTOMER").claim("purpose","NORMAL").issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(600))).build();
        for(String variant:List.of("alg","kid","issuer","sid","aud","purpose","future","jku","typ")) {
            var claims=new JWTClaimsSet.Builder(good);var header=new JWSHeader.Builder(variant.equals("alg")?JWSAlgorithm.RS512:JWSAlgorithm.RS256).type(JOSEObjectType.JWT).keyID(variant.equals("kid")?"wrong":"development-1");
            if(variant.equals("jku")) header.jwkURL(java.net.URI.create("https://attacker.invalid/keys"));
            if(variant.equals("typ")) header.type(new JOSEObjectType("other"));
            switch(variant){case "issuer"->claims.issuer("attacker");case "sid"->claims.claim("sid",null);case "aud"->claims.audience(List.of("ticket-shop-client","ticket-shop-admin"));case "purpose"->claims.claim("purpose","ANY");case "future"->claims.issueTime(Date.from(now.plusSeconds(31)));}
            var jwt=new SignedJWT(header.build(),claims.build());jwt.sign(new RSASSASigner(privateKey));String token=jwt.serialize();
            assertThatThrownBy(()->tokens.verify(token,"CUSTOMER",false)).as(variant).isInstanceOf(AuthProblem.class);
        }
    }
    @Test void noExpiryGraceAndExpiredLogoutStillRequiresSignature() {
        String token=tokens.sign("ADMIN",123,Credentials.random(),Credentials.random(),"NORMAL",now.minusSeconds(600),now);
        assertThatThrownBy(()->tokens.verify(token,"ADMIN",false)).isInstanceOf(AuthProblem.class);
        assertThat(tokens.verify(token,"ADMIN",true).id()).isEqualTo(123);
        assertThatThrownBy(()->tokens.verify("x.y.z","ADMIN",true)).isInstanceOf(AuthProblem.class);
    }
    @Test void developmentNeverSilentlyCreatesEphemeralKeys() {
        var missing=new MockEnvironment();missing.setActiveProfiles("development");assertThatThrownBy(()->new JwtTokens(missing,Clock.systemUTC())).isInstanceOf(IllegalStateException.class);
    }
}
