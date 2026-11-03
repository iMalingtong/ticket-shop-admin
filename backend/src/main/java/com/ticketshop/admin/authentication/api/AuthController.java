package com.ticketshop.admin.authentication.api;

import com.ticketshop.admin.authentication.application.*;
import com.ticketshop.admin.authentication.infrastructure.AuthenticationMapper.Realm;
import com.ticketshop.admin.common.api.*;
import jakarta.servlet.http.*;
import java.time.Clock;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;
import static com.ticketshop.admin.common.api.ErrorCode.*;

@RestController
public class AuthController {
    private final AuthService auth; private final Clock clock; private final Set<String> origins; private final boolean secure; private final String minimum;
    public AuthController(AuthService auth,Clock clock,Environment environment,@Value("${ticket-shop.auth.allowed-origins}") String origins,@Value("${ticket-shop.auth.minimum-client-version:}") String minimum) {
        this.auth=auth; this.clock=clock; this.origins=new HashSet<>(List.of(origins.split(","))); this.secure=!Arrays.asList(environment.getActiveProfiles()).contains("development") && !Arrays.asList(environment.getActiveProfiles()).contains("test"); this.minimum=minimum;
        if(!minimum.isBlank() && !minimum.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) throw new IllegalStateException("最低版本配置无效");
        if(this.origins.isEmpty() || this.origins.stream().anyMatch(v -> !v.matches("https?://[A-Za-z0-9.\\-]+(:[0-9]{1,5})?")) || secure && this.origins.stream().anyMatch(v -> !v.startsWith("https://"))) throw new IllegalStateException("允许来源配置无效");
    }
    public record AdminLogin(String username,String password) {}
    public record ClientLogin(String username,String password,String deviceName,String os,String clientVersion,Integer protocolVersion) {}
    public record Metadata(String clientVersion,Integer protocolVersion) {}
    public record FirstPassword(String newPassword) {}
    public record Password(String currentPassword,String newPassword) {}
    private <T> ApiEnvelope<T> ok(HttpServletRequest request,T data) { return new ApiEnvelope<>(data,AuthResponses.requestId(request),clock.instant()); }
    private String cookie(HttpServletRequest request,String name) {
        String result=null; if(request.getCookies()!=null) for(var c:request.getCookies()) if(c.getName().equals(name)) { if(result!=null) throw new AuthProblem(SESSION_INVALID); result=c.getValue(); } return result;
    }
    private void cookie(HttpServletResponse response,String name,String value,long seconds) { response.addHeader("Set-Cookie",ResponseCookie.from(name,value).httpOnly(true).secure(secure).sameSite("Strict").path("/api/v1/admin").maxAge(seconds).build().toString()); }
    private void clear(HttpServletResponse response) { cookie(response,"tsa_admin_session","",0); cookie(response,"tsa_admin_pre_auth","",0); }
    private void source(HttpServletRequest request,boolean get) {
        String origin=request.getHeader("Origin");
        if(origin!=null) { if(!origins.contains(origin)) throw new AuthProblem(CSRF_INVALID); return; }
        if(!get) throw new AuthProblem(CSRF_INVALID);
        String site=request.getHeader("Sec-Fetch-Site"),referer=request.getHeader("Referer");
        // Fetch Metadata must be same-origin; a same-site sibling is not trusted.
        if(site!=null && !site.equals("same-origin")) throw new AuthProblem(CSRF_INVALID);
        if(referer!=null) { try { var uri=java.net.URI.create(referer); String ref=uri.getScheme()+"://"+uri.getRawAuthority(); if(!origins.contains(ref)) throw new AuthProblem(CSRF_INVALID); } catch(IllegalArgumentException e) { throw new AuthProblem(CSRF_INVALID); } return; }
        if(!"same-origin".equals(site)) throw new AuthProblem(CSRF_INVALID);
    }
    private AuthService.Context admin(HttpServletRequest r,String purpose,boolean logout) { var c=auth.authenticate(Realm.ADMIN,cookie(r,"tsa_admin_session"),purpose,logout); if(!r.getMethod().equals("GET")) { source(r,false); auth.csrf(c,r.getHeader("X-CSRF-Token")); } return c; }
    private String bearer(HttpServletRequest r) { String header=r.getHeader("Authorization"); if(header==null || !header.startsWith("Bearer ") || header.indexOf(',')>=0) throw new AuthProblem(SESSION_INVALID); return header.substring(7); }
    private AuthService.Context client(HttpServletRequest r,String purpose,boolean logout) { return auth.authenticate(Realm.CUSTOMER,bearer(r),purpose,logout); }
    private void metadata(String version,Integer protocol) {
        if(version==null || version.isEmpty() || version.codePointCount(0,version.length())>40 || protocol==null || protocol<1) throw new AuthProblem(PROTOCOL_INVALID);
        if(protocol!=1) throw new AuthProblem(PROTOCOL_UNSUPPORTED,new ApiFailure.Details(null,null,null,null,null,null,null,1));
        if(!minimum.isBlank() && (!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9.\\-]+)?") || compareVersion(version,minimum)<0)) throw new AuthProblem(CLIENT_UPDATE_REQUIRED);
    }
    private int compareVersion(String a,String b) { String[] av=a.split("[-+]",2)[0].split("\\."),bv=b.split("[-+]",2)[0].split("\\."); if(bv.length!=3) throw new IllegalStateException("最低版本配置无效"); for(int i=0;i<3;i++) { int v=new java.math.BigInteger(av[i]).compareTo(new java.math.BigInteger(bv[i])); if(v!=0) return v; } return 0; }
    @GetMapping("/api/v1/admin/auth/csrf") public ApiEnvelope<?> csrf(HttpServletRequest r,HttpServletResponse response) { source(r,true); var result=auth.csrf(cookie(r,"tsa_admin_session"),r.getRemoteAddr()); if(result.preAuth()!=null) { cookie(response,"tsa_admin_session","",0); cookie(response,"tsa_admin_pre_auth",result.preAuth(),600); } return ok(r,result.data()); }
    @PostMapping("/api/v1/admin/auth/login") public ApiEnvelope<?> login(@RequestBody AdminLogin input,HttpServletRequest r,HttpServletResponse response) {
        source(r,false); var result=auth.login(Realm.ADMIN,input.username(),input.password(),r.getRemoteAddr(),Map.of(),cookie(r,"tsa_admin_pre_auth"),r.getHeader("X-CSRF-Token"),AuthResponses.requestId(r));
        cookie(response,"tsa_admin_session",result.token(),result.context().session().get("purpose").equals("NORMAL")?28800:600); cookie(response,"tsa_admin_pre_auth","",0); return ok(r,result.data());
    }
    @GetMapping("/api/v1/admin/auth/me") public ApiEnvelope<?> me(HttpServletRequest r) { return ok(r,auth.summary(admin(r,"NORMAL",false))); }
    @PostMapping("/api/v1/admin/auth/activity") public ApiEnvelope<?> activity(HttpServletRequest r) { return ok(r,auth.activity(admin(r,"NORMAL",false))); }
    @PostMapping("/api/v1/admin/auth/first-password") public ApiEnvelope<?> first(@RequestBody FirstPassword input,HttpServletRequest r,HttpServletResponse response) { var result=auth.changePassword(admin(r,"PASSWORD_CHANGE",false),null,input.newPassword(),true,AuthResponses.requestId(r)); clear(response); return ok(r,result); }
    @PostMapping("/api/v1/admin/auth/password") public ApiEnvelope<?> password(@RequestBody Password input,HttpServletRequest r,HttpServletResponse response) { var result=auth.changePassword(admin(r,"NORMAL",false),input.currentPassword(),input.newPassword(),false,AuthResponses.requestId(r)); clear(response); return ok(r,result); }
    @PostMapping("/api/v1/admin/auth/logout") public ApiEnvelope<?> logout(HttpServletRequest r,HttpServletResponse response) { var result=auth.logout(admin(r,null,true)); clear(response); return ok(r,result); }
    @PostMapping("/api/v1/client/login") public ApiEnvelope<?> clientLogin(@RequestBody ClientLogin input,HttpServletRequest r) {
        metadata(input.clientVersion(),input.protocolVersion()); if(!Set.of("MACOS","WINDOWS").contains(input.os()==null?"":input.os()) || input.deviceName()!=null && (input.deviceName().isBlank() || input.deviceName().codePointCount(0,input.deviceName().length())>100)) throw new AuthProblem(VALIDATION_ERROR);
        var meta=new HashMap<String,Object>(); meta.put("device_name",input.deviceName()); meta.put("os",input.os()); meta.put("client_version",input.clientVersion()); meta.put("protocol_version",input.protocolVersion());
        return ok(r,auth.login(Realm.CUSTOMER,input.username(),input.password(),r.getRemoteAddr(),meta,null,null,AuthResponses.requestId(r)).data());
    }
    @PostMapping("/api/v1/client/first-password") public ApiEnvelope<?> clientFirst(@RequestBody FirstPassword input,HttpServletRequest r) { return ok(r,auth.changePassword(client(r,"PASSWORD_CHANGE",false),null,input.newPassword(),true,AuthResponses.requestId(r))); }
    @PostMapping("/api/v1/client/password") public ApiEnvelope<?> clientPassword(@RequestBody Password input,HttpServletRequest r) { return ok(r,auth.changePassword(client(r,"NORMAL",false),input.currentPassword(),input.newPassword(),false,AuthResponses.requestId(r))); }
    @PostMapping("/api/v1/client/validate") public ApiEnvelope<?> validate(@RequestBody Metadata input,HttpServletRequest r) { metadata(input.clientVersion(),input.protocolVersion()); return ok(r,auth.validate(client(r,"NORMAL",false))); }
    @GetMapping("/api/v1/client/account") public ApiEnvelope<?> account(HttpServletRequest r) { var c=client(r,"NORMAL",false); return ok(r,auth.qualification(c)); }
    @PostMapping("/api/v1/client/logout") public ApiEnvelope<?> clientLogout(HttpServletRequest r) { return ok(r,auth.logout(client(r,null,true))); }
}
