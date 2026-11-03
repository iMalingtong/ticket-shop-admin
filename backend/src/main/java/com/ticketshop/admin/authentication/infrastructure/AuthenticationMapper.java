package com.ticketshop.admin.authentication.infrastructure;

import java.time.Instant;
import java.util.Map;
import org.apache.ibatis.annotations.*;

/** Named authentication operations. Realm is a server enum; XML chooses fixed tables, never SQL interpolation. */
@Mapper
public interface AuthenticationMapper {
    enum Realm { ADMIN, CUSTOMER }
    @Update("UPDATE platform_control SET initialized=TRUE,version=version+1,updated_at=#{now} WHERE id=1 AND initialized=FALSE") int initialize(Instant now);
    @Select("SELECT * FROM platform_control WHERE id=1 FOR UPDATE") Map<String,Object> control();
    @Select("SELECT * FROM login_policy WHERE id=1 FOR UPDATE") Map<String,Object> policy();
    java.util.List<String> sessionIds(@Param("realm") Realm realm,@Param("id") long id);
    Map<String,Object> account(@Param("realm") Realm realm,@Param("key") String key,@Param("id") Long id);
    Map<String,Object> session(@Param("realm") Realm realm,@Param("sid") String sid);
    int insertSession(@Param("realm") Realm realm,@Param("v") Map<String,Object> values);
    int changePassword(@Param("realm") Realm realm,@Param("id") long id,@Param("hash") String hash,@Param("now") Instant now);
    int revoke(@Param("realm") Realm realm,@Param("id") long id,@Param("sid") String sid,@Param("reason") String reason,@Param("now") Instant now);
    @Update("UPDATE admin_session SET csrf_hash=#{hash},version=version+1 WHERE id=#{id}") int csrf(@Param("id") long id,@Param("hash") byte[] hash);
    @Update("UPDATE admin_session SET last_activity_at=#{now},version=version+1 WHERE id=#{id}") int activity(@Param("id") long id,@Param("now") Instant now);
    @Update("UPDATE customer_account SET last_login_at=#{now} WHERE id=#{id}") int lastLogin(@Param("id") long id,@Param("now") Instant now);
    @Update("UPDATE customer_session SET last_validated_at=#{now},version=version+1 WHERE id=#{sessionId}") int validateSession(@Param("sessionId") long sessionId,@Param("now") Instant now);
    @Update("UPDATE customer_account SET last_validated_at=#{now} WHERE id=#{id}") int validateAccount(@Param("id") long id,@Param("now") Instant now);
    @Insert("INSERT INTO admin_pre_auth(id,token_hash,csrf_hash,created_at,expires_at) VALUES(#{id},#{token},#{csrf},#{now},#{expires})")
    int preAuth(@Param("id") long id,@Param("token") byte[] token,@Param("csrf") byte[] csrf,@Param("now") Instant now,@Param("expires") Instant expires);
    @Select("SELECT * FROM admin_pre_auth WHERE token_hash=#{hash} FOR UPDATE") Map<String,Object> preAuthByHash(byte[] hash);
    @Update("UPDATE admin_pre_auth SET consumed_at=#{now} WHERE id=#{id} AND consumed_at IS NULL") int consume(@Param("id") long id,@Param("now") Instant now);
    @Insert("INSERT INTO login_throttle(id,realm,dimension,key_digest,failure_count,window_started_at,updated_at,version) VALUES(#{id},#{realm},#{dimension},#{digest},0,#{now},#{now},0) ON DUPLICATE KEY UPDATE id=id")
    int ensureThrottle(@Param("id") long id,@Param("realm") String realm,@Param("dimension") String dimension,@Param("digest") byte[] digest,@Param("now") Instant now);
    @Select("SELECT * FROM login_throttle WHERE realm=#{realm} AND dimension=#{dimension} AND key_digest=#{digest} FOR UPDATE")
    Map<String,Object> throttle(@Param("realm") String realm,@Param("dimension") String dimension,@Param("digest") byte[] digest);
    @Select("SELECT * FROM login_throttle WHERE realm=#{realm} AND dimension=#{dimension} AND key_digest=#{digest}")
    Map<String,Object> throttleSnapshot(@Param("realm") String realm,@Param("dimension") String dimension,@Param("digest") byte[] digest);
    @Update("UPDATE login_throttle SET failure_count=#{count},window_started_at=#{start},blocked_until=#{blocked},updated_at=#{now},version=version+1 WHERE id=#{id}")
    int updateThrottle(@Param("id") long id,@Param("count") int count,@Param("start") Instant start,@Param("blocked") Instant blocked,@Param("now") Instant now);
}
