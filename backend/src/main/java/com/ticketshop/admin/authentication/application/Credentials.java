package com.ticketshop.admin.authentication.application;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.*;
import org.springframework.stereotype.Component;
import static com.ticketshop.admin.common.api.ErrorCode.*;

@Component
public class Credentials {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final PasswordEncoder encoder = new DelegatingPasswordEncoder("argon2id",
        Map.of("argon2id",new Argon2PasswordEncoder(16,32,1,19456,2)));
    private final String dummy = encoder.encode(random());
    private final Semaphore capacity = new Semaphore(4);
    public static String random() { byte[] bytes=new byte[32]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    public static byte[] hash(String value) { try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); } catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
    public static String hex(byte[] value) { return HexFormat.of().formatHex(value); }
    public static boolean equal(byte[] digest,String value) { return digest!=null && value!=null && value.matches("[A-Za-z0-9_-]{43}") && MessageDigest.isEqual(digest,hash(value)); }
    public static String username(String value) { if(value==null || !value.matches("[A-Za-z0-9._-]{3,64}")) throw new AuthProblem(VALIDATION_ERROR); return value.toLowerCase(Locale.ROOT); }
    public static void password(String value) {
        if(value==null || value.codePointCount(0,value.length())<12 || value.codePointCount(0,value.length())>128 ||
            value.codePoints().anyMatch(c -> c>=0xD800 && c<=0xDFFF)) throw new AuthProblem(VALIDATION_ERROR);
    }
    public String encode(String value) { password(value); if(!capacity.tryAcquire()) throw new AuthProblem(SERVICE_UNAVAILABLE); try { return encoder.encode(value); } finally { capacity.release(); } }
    public boolean matches(String value,String hash) { password(value); if(!capacity.tryAcquire()) throw new AuthProblem(SERVICE_UNAVAILABLE);
        try { return encoder.matches(value,hash==null ? dummy : hash); } catch(IllegalArgumentException e) { return false; } finally { capacity.release(); } }
}
