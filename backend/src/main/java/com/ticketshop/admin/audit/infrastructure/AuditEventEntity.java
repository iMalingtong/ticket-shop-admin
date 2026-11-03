package com.ticketshop.admin.audit.infrastructure;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

/** 管理操作审计（只追加）。仅用于持久化，禁止作为HTTP响应。 */
@TableName(value = "audit_event", autoResultMap = true)
public class AuditEventEntity {
    /** 主键（正数且不超过Java Long上限） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }

    /** 操作者身份域 */
    private String actorType;
    public String getActorType() { return actorType; }
    public void setActorType(String value) { this.actorType = value; }

    /** 操作者多态标识 */
    private Long actorId;
    public Long getActorId() { return actorId; }
    public void setActorId(Long value) { this.actorId = value; }

    /** 操作者名称快照 */
    private String actorNameSnapshot;
    public String getActorNameSnapshot() { return actorNameSnapshot; }
    public void setActorNameSnapshot(String value) { this.actorNameSnapshot = value; }

    /** 目标类型 */
    private String targetType;
    public String getTargetType() { return targetType; }
    public void setTargetType(String value) { this.targetType = value; }

    /** 目标多态标识 */
    private Long targetId;
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long value) { this.targetId = value; }

    /** 操作代码 */
    private String action;
    public String getAction() { return action; }
    public void setAction(String value) { this.action = value; }

    /** 脱敏前值白名单 */
    private String beforeValues;
    public String getBeforeValues() { return beforeValues; }
    public void setBeforeValues(String value) { this.beforeValues = value; }

    /** 脱敏后值白名单 */
    private String afterValues;
    public String getAfterValues() { return afterValues; }
    public void setAfterValues(String value) { this.afterValues = value; }

    /** 操作原因 */
    private String reason;
    public String getReason() { return reason; }
    public void setReason(String value) { this.reason = value; }

    /** 操作结果 */
    private String result;
    public String getResult() { return result; }
    public void setResult(String value) { this.result = value; }

    /** 失败错误代码 */
    private String errorCode;
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String value) { this.errorCode = value; }

    /** 服务器请求标识 */
    private String requestId;
    public String getRequestId() { return requestId; }
    public void setRequestId(String value) { this.requestId = value; }

    /** 发生时刻UTC */
    private Instant occurredAt;
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant value) { this.occurredAt = value; }

}
