package com.ticketshop.admin.authentication.application;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jwt.*;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import static com.ticketshop.admin.common.api.ErrorCode.*;

@Component
public class JwtTokens {
    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final byte[] hmac;
    private final String issuer,kid;
    private final Clock clock;
    public JwtTokens(Environment env,Clock clock) {
        this.clock=clock; issuer="urn:ticket-shop-admin:"+Arrays.stream(env.getActiveProfiles()).filter(p -> Set.of("test","development","production").contains(p)).findFirst().orElseThrow();
        kid=env.getProperty("ticket-shop.auth.key-id","development-1");
        try {
            if(!kid.matches("[A-Za-z0-9._-]{1,64}")) throw new IllegalArgumentException();
            var factory=KeyFactory.getInstance("RSA");
            String encoded=env.getProperty("ticket-shop.auth.private-key");
            String pub=env.getProperty("ticket-shop.auth.public-key");
            String secret=env.getProperty("ticket-shop.auth.hmac-key");
            if(encoded==null || encoded.isBlank() || pub==null || pub.isBlank() || secret==null || secret.isBlank()) {
                // Explicit isolated tests only. A local/production service never silently rotates its keys on restart.
                if(!Arrays.asList(env.getActiveProfiles()).contains("test")) throw new IllegalArgumentException();
                var generator=KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); var pair=generator.generateKeyPair();
                privateKey=(RSAPrivateKey)pair.getPrivate(); publicKey=(RSAPublicKey)pair.getPublic(); hmac=Credentials.hash(Credentials.random());
            } else {
                privateKey=(RSAPrivateKey)factory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(encoded)));
                publicKey=(RSAPublicKey)factory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pub)));
                hmac=Base64.getDecoder().decode(secret);
            }
            if(publicKey.getModulus().bitLength()<2048 || !privateKey.getModulus().equals(publicKey.getModulus()) || hmac.length<32) throw new IllegalArgumentException();
        } catch(Exception e) { throw new IllegalStateException("JWT/HMAC配置缺失或无效；需显式注入本项目签名配置"); }
    }
    private String audience(String realm) { return realm.equals("ADMIN") ? "ticket-shop-admin" : "ticket-shop-client"; }
    public String sign(String realm,long id,String sid,String jti,String purpose,Instant now,Instant expires) {
        try {
            var claims=new JWTClaimsSet.Builder().issuer(issuer).audience(audience(realm)).subject(Long.toString(id)).jwtID(jti)
                .claim("principalType",realm).claim("sid",sid).claim("purpose",purpose).issueTime(Date.from(now)).expirationTime(Date.from(expires)).build();
            var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).keyID(kid).build(),claims);
            jwt.sign(new RSASSASigner(privateKey)); return jwt.serialize();
        } catch(JOSEException e) { throw new AuthProblem(SERVICE_UNAVAILABLE); }
    }
    public record Claims(long id,String sid,String jti,String purpose,Instant issued,Instant expires) {}
    public Claims verify(String token,String realm,boolean allowExpired) {
        try {
            if(token==null || token.length()>8192) throw new IllegalArgumentException();
            var jwt=SignedJWT.parse(token); var head=jwt.getHeader();
            if(!JWSAlgorithm.RS256.equals(head.getAlgorithm()) || !JOSEObjectType.JWT.equals(head.getType()) || !kid.equals(head.getKeyID()) ||
                head.getJWKURL()!=null || head.getX509CertURL()!=null || head.getJWK()!=null || (head.getCriticalParams()!=null && !head.getCriticalParams().isEmpty()) ||
                !jwt.verify(new RSASSAVerifier(publicKey))) throw new IllegalArgumentException();
            var c=jwt.getJWTClaimsSet(); String sub=c.getSubject(), sid=c.getStringClaim("sid"),jti=c.getJWTID(),purpose=c.getStringClaim("purpose");
            Object iat=c.getClaim("iat"), exp=c.getClaim("exp");
            var raw=jwt.getPayload().toJSONObject();
            if(!(raw.get("iat") instanceof Long || raw.get("iat") instanceof Integer) || !(raw.get("exp") instanceof Long || raw.get("exp") instanceof Integer)) throw new IllegalArgumentException();
            if(!issuer.equals(c.getIssuer()) || !List.of(audience(realm)).equals(c.getAudience()) || !realm.equals(c.getStringClaim("principalType")) ||
                sub==null || !sub.matches("[1-9][0-9]{0,18}") || sid==null || !sid.matches("[A-Za-z0-9_-]{43}") || jti==null || !jti.matches("[A-Za-z0-9_-]{43}") ||
                !Set.of("NORMAL","PASSWORD_CHANGE").contains(purpose) || !(iat instanceof Date) || !(exp instanceof Date)) throw new IllegalArgumentException();
            long id=Long.parseLong(sub); Instant issued=c.getIssueTime().toInstant(),expires=c.getExpirationTime().toInstant(),now=clock.instant();
            if(id<=0 || issued.isAfter(now.plusSeconds(30)) || !expires.isAfter(issued)) throw new IllegalArgumentException();
            if(!allowExpired && !now.isBefore(expires)) throw new AuthProblem(SESSION_EXPIRED);
            return new Claims(id,sid,jti,purpose,issued,expires);
        } catch(AuthProblem e) { throw e; } catch(Exception e) { throw new AuthProblem(SESSION_INVALID); }
    }
    public byte[] digest(String scope,String value) {
        try { var mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(hmac,"HmacSHA256")); return mac.doFinal((scope+":"+value).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        catch(GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
}
