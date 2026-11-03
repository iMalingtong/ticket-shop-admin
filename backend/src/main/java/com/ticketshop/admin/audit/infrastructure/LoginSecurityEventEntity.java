package com.ticketshop.admin.audit.infrastructure;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

/** 登录安全事件（只追加）。仅用于持久化，禁止作为HTTP响应。 */
@TableName(value = "login_security_event", autoResultMap = true)
public class LoginSecurityEventEntity {
    /** 主键（正数且不超过Java Long上限） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }

    /** 身份域 */
    private String identityType;
    public String getIdentityType() { return identityType; }
    public void setIdentityType(String value) { this.identityType = value; }

    /** 已确认账号多态标识 */
    private Long accountId;
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long value) { this.accountId = value; }

    /** 规范账号HMAC摘要 */
    private byte[] accountKeyDigest;
    public byte[] getAccountKeyDigest() { return accountKeyDigest; }
    public void setAccountKeyDigest(byte[] value) { this.accountKeyDigest = value; }

    /** 事件代码 */
    private String eventType;
    public String getEventType() { return eventType; }
    public void setEventType(String value) { this.eventType = value; }

    /** 结果 */
    private String result;
    public String getResult() { return result; }
    public void setResult(String value) { this.result = value; }

    /** 原因代码 */
    private String code;
    public String getCode() { return code; }
    public void setCode(String value) { this.code = value; }

    /** 必要来源IP二进制 */
    private byte[] sourceIp;
    public byte[] getSourceIp() { return sourceIp; }
    public void setSourceIp(byte[] value) { this.sourceIp = value; }

    /** 客户端版本 */
    private String clientVersion;
    public String getClientVersion() { return clientVersion; }
    public void setClientVersion(String value) { this.clientVersion = value; }

    /** 服务器请求标识 */
    private String requestId;
    public String getRequestId() { return requestId; }
    public void setRequestId(String value) { this.requestId = value; }

    /** 发生时刻UTC */
    private Instant occurredAt;
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant value) { this.occurredAt = value; }

}
