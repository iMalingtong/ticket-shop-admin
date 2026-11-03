package com.ticketshop.admin.authentication.application;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.ticketshop.admin.authentication.infrastructure.AuthenticationMapper;
import com.ticketshop.admin.authentication.infrastructure.AuthenticationMapper.Realm;
import com.ticketshop.admin.administrator.infrastructure.*;
import com.ticketshop.admin.audit.infrastructure.*;
import com.ticketshop.admin.common.api.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.ticketshop.admin.common.api.ErrorCode.*;

@Service
public class AuthService {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(AuthService.class);
    private final AuthenticationMapper db;
    private final Credentials passwords;
    private final JwtTokens tokens;
    private final AuthCache cache;
    private final Clock clock;
    private final PlatformTransactionManager transactions;
    private final AdminAccountMapper admins;
    private final AuditEventMapper audits;
    private final LoginSecurityEventMapper events;
    public AuthService(AuthenticationMapper db,Credentials passwords,JwtTokens tokens,AuthCache cache,Clock clock,PlatformTransactionManager transactions,
                       AdminAccountMapper admins,AuditEventMapper audits,LoginSecurityEventMapper events) {
        this.db=db; this.passwords=passwords; this.tokens=tokens; this.cache=cache; this.clock=clock; this.transactions=transactions; this.admins=admins; this.audits=audits; this.events=events;
    }
    public <T> T tx(Supplier<T> action) { var tx=new TransactionTemplate(transactions); tx.setTimeout(RequestBudget.seconds()); return tx.execute(status -> action.get()); }
    static long num(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }
    static String str(Map<String,Object> row,String key) { return (String)row.get(key); }
    static boolean flag(Map<String,Object> row,String key) { var v=row.get(key); return Boolean.TRUE.equals(v) || v instanceof Number n && n.intValue()!=0; }
    static Instant time(Map<String,Object> row,String key) { var v=row.get(key); if(v==null) return null; if(v instanceof Instant i) return i; if(v instanceof LocalDateTime t) return t.toInstant(ZoneOffset.UTC); return ((java.sql.Timestamp)v).toLocalDateTime().toInstant(ZoneOffset.UTC); }
    private Instant now() { return clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS); }
    private void control() { var c=db.control(); if(c==null || flag(c,"recovery_mode")) throw new AuthProblem(MAINTENANCE); }
    private void eligible(Realm realm,Map<String,Object> a,Instant now) {
        if(a==null) throw new AuthProblem(SESSION_INVALID);
        var reasons=new ArrayList<String>(); String status=str(a,"status");
        if("FROZEN".equals(status)) reasons.add("ACCOUNT_FROZEN");
        if("DISABLED".equals(status)) reasons.add("ACCOUNT_DISABLED");
        Instant from=realm==Realm.CUSTOMER ? time(a,"valid_from") : null, until=realm==Realm.CUSTOMER ? time(a,"valid_until") : null;
        if(from!=null && now.isBefore(from)) reasons.add("ACCOUNT_NOT_STARTED");
        if(until!=null && !now.isBefore(until)) reasons.add("ACCOUNT_EXPIRED");
        if(!reasons.isEmpty()) throw AuthProblem.denied(reasons,status,from,until);
    }
    private void audit(long id,String name,String action,String requestId,Instant now) {
        var e=new AuditEventEntity(); e.setId(IdWorker.getId()); e.setActorType("ADMIN"); e.setActorId(id); e.setActorNameSnapshot(name); e.setTargetType("ADMIN"); e.setTargetId(id);
        e.setAction(action); e.setResult("SUCCESS"); e.setRequestId(requestId); e.setOccurredAt(now); audits.insert(e);
    }
    private void event(Realm realm,Long id,byte[] digest,String result,ErrorCode code,String requestId,Instant now,String source,Map<String,Object> metadata) {
        var e=new LoginSecurityEventEntity(); e.setId(IdWorker.getId()); e.setIdentityType(realm.name()); e.setAccountId(id); e.setAccountKeyDigest(digest); e.setEventType("LOGIN");
        try { if(source.matches("[0-9a-fA-F:.]+")) e.setSourceIp(java.net.InetAddress.getByName(source).getAddress()); } catch(java.net.UnknownHostException ignored) {}
        e.setClientVersion((String)metadata.get("client_version"));
        e.setResult(result); e.setCode(code==null ? "LOGIN_SUCCESS" : code.name()); e.setRequestId(requestId); e.setOccurredAt(now); events.insert(e);
    }
    public long bootstrap(String username,String displayName,String password,String requestId) {
        String key=Credentials.username(username); if(displayName==null || displayName.isBlank() || displayName.codePointCount(0,displayName.length())>64) throw new AuthProblem(VALIDATION_ERROR);
        String hash=passwords.encode(password);
        return tx(() -> { var c=db.control(); if(flag(c,"initialized") || flag(c,"recovery_mode") || admins.selectCount(null)!=0) throw new AuthProblem(INVALID_STATE_TRANSITION);
            Instant now=now(); var a=new AdminAccountEntity(); a.setUsername(username); a.setUsernameKey(key); a.setDisplayName(displayName); a.setPasswordHash(hash); a.setPasswordVersion(0L);
            a.setRole("SUPER_ADMIN"); a.setStatus("ACTIVE"); a.setMustChangePassword(true); a.setVersion(0L); a.setCreatedAt(now); a.setUpdatedAt(now); admins.insert(a);
            db.initialize(now); audit(a.getId(),username,"BOOTSTRAP",requestId,now); return a.getId(); });
    }
    private void ensureThrottle(Realm realm,String dimension,byte[] digest) { tx(() -> { db.ensureThrottle(IdWorker.getId(),realm.name(),dimension,digest,now()); return null; }); }
    private Map<String,Object> throttle(Realm realm,String dimension,byte[] digest,Instant now) {
        var row=db.throttle(realm.name(),dimension,digest);
        Instant block=time(row,"blocked_until"); if(block!=null && now.isBefore(block)) return row;
        if(!now.isBefore(time(row,"window_started_at").plusSeconds(900))) { db.updateThrottle(num(row,"id"),0,now,null,now); row=new HashMap<>(row); row.put("failure_count",0); row.put("window_started_at",now); row.remove("blocked_until"); }
        return row;
    }
    private void failThrottle(Map<String,Object> row,int limit,Instant now) { int count=(int)num(row,"failure_count")+1; db.updateThrottle(num(row,"id"),count,time(row,"window_started_at"),count>=limit?now.plusSeconds(900):null,now); }
    private void throttleCache(Realm realm,Map<String,Object> row) {
        String dimension=str(row,"dimension"); Instant until=time(row,"window_started_at").plusSeconds(900),block=time(row,"blocked_until"); if(block!=null && block.isAfter(until)) until=block;
        cache.write("login-throttle:"+realm+":"+dimension+":"+Credentials.hex((byte[])row.get("key_digest")),Map.of("schemaVersion",1,"failureCount",num(row,"failure_count"),"windowStartedAt",time(row,"window_started_at").toString(),"blockedUntil",block==null?"":block.toString(),"version",Long.toString(num(row,"version"))),until);
    }
    public record Login(Map<String,Object> data,String token,String csrf,AuthProblem error,Context context) {}
    public Login login(Realm realm,String username,String password,String source,Map<String,Object> metadata,String preAuth,String csrf,String requestId) {
        String key=Credentials.username(username); Credentials.password(password); byte[] accountDigest=tokens.digest(realm+":ACCOUNT",key), sourceDigest=tokens.digest(realm+":SOURCE",source);
        cache.read("login-throttle:"+realm+":SOURCE:"+Credentials.hex(sourceDigest));
        ensureThrottle(realm,"SOURCE",sourceDigest);
        AuthProblem sourceBlock=tx(() -> {
            var row=throttle(realm,"SOURCE",sourceDigest,now()); Instant until=time(row,"blocked_until");
            if(until!=null && now().isBefore(until)) { event(realm,null,accountDigest,"THROTTLED",LOGIN_RATE_LIMITED,requestId,now(),source,metadata); return AuthProblem.limited((int)Duration.between(now(),until).toSeconds()); }
            return null;
        });
        if(sourceBlock!=null) throw sourceBlock;
        ensureThrottle(realm,"ACCOUNT",accountDigest);
        var result=tx(() -> {
            Instant now=now(); var sourceRow=throttle(realm,"SOURCE",sourceDigest,now); var accountRow=throttle(realm,"ACCOUNT",accountDigest,now);
            for(var row:List.of(sourceRow,accountRow)) { Instant blocked=time(row,"blocked_until"); if(blocked!=null && now.isBefore(blocked)) { event(realm,null,accountDigest,"THROTTLED",LOGIN_RATE_LIMITED,requestId,now,source,metadata); return new Login(null,null,null,AuthProblem.limited((int)Duration.between(now,blocked).toSeconds()),null); } }
            if(realm==Realm.ADMIN) {
                if(preAuth==null || !preAuth.matches("[A-Za-z0-9_-]{43}")) throw new AuthProblem(CSRF_INVALID);
                var pre=db.preAuthByHash(Credentials.hash(preAuth));
                if(pre==null || pre.get("consumed_at")!=null || !now.isBefore(time(pre,"expires_at")) || !Credentials.equal((byte[])pre.get("csrf_hash"),csrf)) throw new AuthProblem(CSRF_INVALID);
            }
            control(); var policy=db.policy(); var a=db.account(realm,key,null);
            if(!passwords.matches(password,a==null?null:str(a,"password_hash"))) {
                failThrottle(sourceRow,50,now); failThrottle(accountRow,5,now); event(realm,null,accountDigest,"FAILURE",INVALID_CREDENTIALS,requestId,now,source,metadata);
                return new Login(null,null,null,new AuthProblem(INVALID_CREDENTIALS),null);
            }
            try { eligible(realm,a,now); } catch(AuthProblem e) { event(realm,num(a,"id"),accountDigest,"FAILURE",e.code(),requestId,now,source,metadata); return new Login(null,null,null,e,null); }
            db.updateThrottle(num(accountRow,"id"),0,now,null,now);
            String purpose=flag(a,"must_change_password")?"PASSWORD_CHANGE":"NORMAL",sid=Credentials.random(),jti=Credentials.random(),nextCsrf=Credentials.random();
            Instant expires=now.plusSeconds(purpose.equals("PASSWORD_CHANGE")?600:realm==Realm.ADMIN?28800:num(policy,"client_max_hours")*3600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
            String token=tokens.sign(realm.name(),num(a,"id"),sid,jti,purpose,now,expires);
            var session=new HashMap<String,Object>(); session.put("id",IdWorker.getId()); session.put("owner",num(a,"id")); session.put("sid",sid); session.put("jti",jti); session.put("purpose",purpose);
            session.put("token_hash",Credentials.hash(token)); session.put("csrf_hash",Credentials.hash(nextCsrf)); session.put("authenticated_at",now); session.put("expires_at",expires); session.put("version",0L);
            session.put("last_activity_at",now); session.put("policy_version",num(policy,"version")); session.putAll(metadata); db.insertSession(realm,session);
            if(realm==Realm.ADMIN) { var pre=db.preAuthByHash(Credentials.hash(preAuth)); db.consume(num(pre,"id"),now); audit(num(a,"id"),str(a,"username"),"LOGIN",requestId,now); }
            else if(purpose.equals("NORMAL")) db.lastLogin(num(a,"id"),now);
            event(realm,num(a,"id"),accountDigest,"SUCCESS",null,requestId,now,source,metadata);
            var context=new Context(realm,a,session,policy,token); Map<String,Object> data;
            if(purpose.equals("PASSWORD_CHANGE")) { data=new HashMap<>(Map.of("nextAction","CHANGE_PASSWORD","changeExpiresAt",expires)); if(realm==Realm.CUSTOMER) data.put("token",token); else data.put("csrfToken",nextCsrf); }
            else if(realm==Realm.ADMIN) data=Map.of("nextAction","NONE","administrator",summary(context),"csrfToken",nextCsrf);
            else data=Map.of("nextAction","NONE","token",token,"qualification",qualification(context));
            return new Login(data,token,nextCsrf,null,context);
        });
        // Persisted counters survive a missing cache or process restart; these reads never clear them.
        throttleCache(realm,db.throttleSnapshot(realm.name(),"SOURCE",sourceDigest)); throttleCache(realm,db.throttleSnapshot(realm.name(),"ACCOUNT",accountDigest));
        if(result.error()!=null) throw result.error(); sync(result.context());
        if(realm==Realm.ADMIN) cache.delete("pre-auth:ADMIN:"+Credentials.hex(Credentials.hash(preAuth)));
        return result;
    }
    public record Context(Realm realm,Map<String,Object> account,Map<String,Object> session,Map<String,Object> policy,String token) {}
    public Context authenticate(Realm realm,String token,String purpose,boolean logout) {
        var claims=tokens.verify(token,realm.name(),logout); cache.read("session:"+realm+":"+claims.sid());
        var context=tx(() -> locked(realm,token,claims,purpose,logout));
        if(!logout) sync(context); return context;
    }
    private Context locked(Realm realm,String token,JwtTokens.Claims c,String purpose,boolean logout) {
        control(); var policy=db.policy(); var account=db.account(realm,null,c.id()); var session=db.session(realm,c.sid()); Instant now=now();
        if(account==null || session==null || num(session,realm==Realm.ADMIN?"admin_id":"account_id")!=c.id() || !c.jti().equals(str(session,"jti")) || !c.purpose().equals(str(session,"purpose")) || !java.security.MessageDigest.isEqual((byte[])session.get("token_hash"),Credentials.hash(token))) throw new AuthProblem(SESSION_INVALID);
        if(!logout) {
            if(session.get("revoked_at")!=null) throw new AuthProblem(c.purpose().equals("PASSWORD_CHANGE")?PASSWORD_CHANGE_TOKEN_INVALID:SESSION_REVOKED);
            if(!now.isBefore(time(session,"expires_at")) || realm==Realm.ADMIN && c.purpose().equals("NORMAL") && !now.isBefore(time(session,"last_activity_at").plusSeconds(1800))) throw new AuthProblem(SESSION_EXPIRED);
            if(!c.purpose().equals(purpose)) throw new AuthProblem(c.purpose().equals("PASSWORD_CHANGE")?PASSWORD_CHANGE_REQUIRED:FORBIDDEN);
            if(c.purpose().equals("NORMAL") && flag(account,"must_change_password")) throw new AuthProblem(PASSWORD_CHANGE_REQUIRED);
            if(c.purpose().equals("PASSWORD_CHANGE") && !flag(account,"must_change_password")) throw new AuthProblem(PASSWORD_CHANGE_TOKEN_INVALID);
            eligible(realm,account,now);
            if(realm==Realm.CUSTOMER && c.purpose().equals("NORMAL")) {
                Instant max=time(session,"authenticated_at").plusSeconds(num(policy,"client_max_hours")*3600);
                if(!now.isBefore(max)) throw new AuthProblem(SESSION_EXPIRED);
                if(max.isBefore(time(session,"expires_at"))) session.put("expires_at",max);
            }
        }
        return new Context(realm,account,session,policy,token);
    }
    public void sync(Context c) {
        var s=c.session(); Instant until=time(s,"expires_at"); if(c.realm()==Realm.ADMIN && "NORMAL".equals(str(s,"purpose"))) until=earlier(until,time(s,"last_activity_at").plusSeconds(1800));
        if(c.realm()==Realm.CUSTOMER) until=earlier(until,time(c.account(),"valid_until"));
        var value=new HashMap<String,Object>(); value.put("schemaVersion",1); value.put("sessionId",Long.toString(num(s,"id"))); value.put("accountId",Long.toString(num(c.account(),"id")));
        value.put("sessionVersion",Long.toString(num(s,"version"))); value.put("accountVersion",Long.toString(num(c.account(),"version"))); value.put("passwordVersion",Long.toString(num(c.account(),"password_version")));
        value.put("purpose",str(s,"purpose")); value.put("tokenHash",Credentials.hex((byte[])s.get("token_hash"))); value.put("expiresAt",time(s,"expires_at").toString());
        String activity=c.realm()==Realm.ADMIN?"last_activity_at":"last_validated_at"; if(time(s,activity)!=null) value.put(c.realm()==Realm.ADMIN?"lastActivityAt":"lastValidatedAt",time(s,activity).toString());
        cache.write("session:"+c.realm()+":"+str(s,"sid"),value,until);
    }
    static Instant earlier(Instant a,Instant b) { return a.isBefore(b)?a:b; }
    public Map<String,Object> summary(Context c) {
        var a=c.account(); return Map.of("id",Long.toString(num(a,"id")),"username",str(a,"username"),"displayName",str(a,"display_name"),"role",str(a,"role"),"permissions",permissions(str(a,"role")),"sessionExpiresAt",time(c.session(),"expires_at"),"idleExpiresAt",earlier(time(c.session(),"expires_at"),time(c.session(),"last_activity_at").plusSeconds(1800)));
    }
    public static List<String> permissions(String role) {
        var customer=List.of("dashboard:read","customer:read","customer:create","customer:update","customer:state","customer:validity","customer:password-reset","customer-session:read","customer-session:revoke","customer-audit:read","customer-security:read","customer-security:unlock","policy:read");
        if(!role.equals("SUPER_ADMIN")) return customer;
        var all=new ArrayList<>(customer); all.addAll(List.of("admin:read","admin:create","admin:update","admin:state","admin:password-reset","admin-audit:read","admin-security:read","admin-security:unlock","policy:update")); return all;
    }
    public Map<String,Object> qualification(Context c) {
        var a=c.account(); return Map.of("qualified",true,"account",Map.of("id",Long.toString(num(a,"id")),"username",str(a,"username"),"displayName",str(a,"display_name"),"status",str(a,"status")),"validFrom",time(a,"valid_from"),"validUntil",time(a,"valid_until"),"authenticatedAt",time(c.session(),"authenticated_at"),"expiresAt",time(c.session(),"expires_at"),"nextValidationAfterSeconds",300,"networkWindowSeconds",600,"policyVersion",Long.toString(num(c.policy(),"version")));
    }
    public Map<String,Object> validate(Context old) {
        var c=tx(() -> { var current=locked(old.realm(),old.token(),tokens.verify(old.token(),old.realm().name(),false),"NORMAL",false); Instant now=now(); db.validateAccount(num(current.account(),"id"),now); db.validateSession(num(current.session(),"id"),now); current.session().put("last_validated_at",now); return current; }); sync(c); return qualification(c);
    }
    public Map<String,Object> activity(Context old) {
        var c=tx(() -> { var current=locked(Realm.ADMIN,old.token(),tokens.verify(old.token(),"ADMIN",false),"NORMAL",false); Instant now=now(); db.activity(num(current.session(),"id"),now); current.session().put("last_activity_at",now); return current; }); sync(c);
        return Map.of("sessionExpiresAt",time(c.session(),"expires_at"),"idleExpiresAt",earlier(time(c.session(),"expires_at"),time(c.session(),"last_activity_at").plusSeconds(1800)));
    }
    public Map<String,Object> changePassword(Context old,String currentPassword,String newPassword,boolean first,String requestId) {
        Credentials.password(newPassword); if(!first) { if(!passwords.matches(currentPassword,str(old.account(),"password_hash"))) throw new AuthProblem(INVALID_CREDENTIALS); }
        if(passwords.matches(newPassword,str(old.account(),"password_hash"))) throw new AuthProblem(VALIDATION_ERROR);
        String hash=passwords.encode(newPassword);
        var c=tx(() -> { var locked=locked(old.realm(),old.token(),tokens.verify(old.token(),old.realm().name(),false),first?"PASSWORD_CHANGE":"NORMAL",false);
            if(num(locked.account(),"password_version")!=num(old.account(),"password_version")) throw new AuthProblem(SESSION_REVOKED);
            db.changePassword(old.realm(),num(locked.account(),"id"),hash,now()); db.revoke(old.realm(),num(locked.account(),"id"),null,"PASSWORD_CHANGED",now());
            if(old.realm()==Realm.ADMIN) audit(num(locked.account(),"id"),str(locked.account(),"username"),first?"FIRST_PASSWORD":"PASSWORD_CHANGED",requestId,now()); return locked; });
        String status=invalidateAll(c,requestId); return Map.of("nextAction","LOGIN","cacheSyncStatus",status);
    }
    public Map<String,Object> logout(Context old) { tx(() -> { var c=locked(old.realm(),old.token(),tokens.verify(old.token(),old.realm().name(),true),null,true); db.revoke(old.realm(),num(c.account(),"id"),str(c.session(),"sid"),"LOGOUT",now()); return null; }); return Map.of("revoked",true,"cacheSyncStatus",deleteCache(old)); }
    private String invalidateAll(Context c,String requestId) {
        try { for(String sid:db.sessionIds(c.realm(),num(c.account(),"id"))) cache.delete("session:"+c.realm()+":"+sid); return "SYNCED"; }
        catch(Exception e) { LOG.warn("密码修改已提交，缓存同步待处理 requestId={}",requestId); return "PENDING"; }
    }
    private String deleteCache(Context c) { try { cache.delete("session:"+c.realm()+":"+str(c.session(),"sid")); return "SYNCED"; } catch(AuthProblem e) { LOG.warn("退出已提交，缓存同步待处理"); return "PENDING"; } }
    public void csrf(Context c,String csrf) { if(!Credentials.equal((byte[])c.session().get("csrf_hash"),csrf)) throw new AuthProblem(CSRF_INVALID); }
    public record Csrf(Map<String,Object> data,String preAuth) {}
    public Csrf csrf(String token,String source) {
        // Independent, persistent source counter limits anonymous row creation, without altering login failures.
        byte[] digest=tokens.digest("ADMIN:CSRF",source); cache.read("pre-auth:ADMIN:availability");
        ensureThrottle(Realm.ADMIN,"SOURCE",digest);
        tx(() -> { var row=throttle(Realm.ADMIN,"SOURCE",digest,now()); Instant block=time(row,"blocked_until"); if(block!=null && now().isBefore(block)) throw AuthProblem.limited((int)Duration.between(now(),block).toSeconds()); failThrottle(row,60,now()); return null; });
        String value=Credentials.random();
        if(token!=null) {
            Context c=null;
            try { var claims=tokens.verify(token,"ADMIN",false); c=authenticate(Realm.ADMIN,token,claims.purpose(),false); } catch(AuthProblem e) { if(e.code().httpStatus()!=401 && e.code()!=ACCOUNT_DISABLED) throw e; }
            if(c!=null) { var old=c; Instant expires=time(c.session(),"expires_at"); tx(() -> { var current=locked(Realm.ADMIN,token,tokens.verify(token,"ADMIN",false),str(old.session(),"purpose"),false); db.csrf(num(current.session(),"id"),Credentials.hash(value)); return null; }); return new Csrf(Map.of("csrfToken",value,"expiresAt",expires),null); }
        }
        String pre=Credentials.random(); Instant now=now(),expires=now.plusSeconds(600); long id=IdWorker.getId(); tx(() -> { db.preAuth(id,Credentials.hash(pre),Credentials.hash(value),now,expires); return null; });
        cache.write("pre-auth:ADMIN:"+Credentials.hex(Credentials.hash(pre)),Map.of("schemaVersion",1,"preAuthId",Long.toString(id),"expiresAt",expires.toString()),expires);
        return new Csrf(Map.of("csrfToken",value,"expiresAt",expires),pre);
    }
}
