package com.ticketshop.admin.audit.infrastructure;

import org.apache.ibatis.annotations.*;

/** Append-only contract: no update/delete operations. Caller supplies the generated id and sanitized values. */
@Mapper
public interface LoginSecurityEventMapper {
    @Insert("INSERT INTO login_security_event (id,identity_type,account_id,account_key_digest,event_type,result,code,source_ip,client_version,request_id,occurred_at) VALUES (#{id},#{identityType},#{accountId},#{accountKeyDigest},#{eventType},#{result},#{code},#{sourceIp},#{clientVersion},#{requestId},#{occurredAt})")
    int insert(LoginSecurityEventEntity event);
}
