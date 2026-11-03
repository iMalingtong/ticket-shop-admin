package com.ticketshop.admin.audit.infrastructure;

import org.apache.ibatis.annotations.*;

/** Append-only contract: no update/delete operations. Caller supplies the generated id and sanitized values. */
@Mapper
public interface AuditEventMapper {
    @Insert("INSERT INTO audit_event (id,actor_type,actor_id,actor_name_snapshot,target_type,target_id,action,before_values,after_values,reason,result,error_code,request_id,occurred_at) VALUES (#{id},#{actorType},#{actorId},#{actorNameSnapshot},#{targetType},#{targetId},#{action},#{beforeValues},#{afterValues},#{reason},#{result},#{errorCode},#{requestId},#{occurredAt})")
    int insert(AuditEventEntity event);
}
