package com.ticketshop.admin.session.infrastructure;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

/** 客户JWT会话事实。仅用于持久化，禁止作为HTTP响应。 */
@TableName(value = "customer_session", autoResultMap = true)
public class CustomerSessionEntity {
    /** 主键（正数且不超过Java Long上限） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }

    /** 客户账号 */
    private Long accountId;
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long value) { this.accountId = value; }

    /** 32字节随机会话标识Base64URL */
    private String sid;
    public String getSid() { return sid; }
    public void setSid(String value) { this.sid = value; }

    /** 32字节随机JWT标识Base64URL */
    private String jti;
    public String getJti() { return jti; }
    public void setJti(String value) { this.jti = value; }

    /** 完整JWT的SHA256摘要 */
    private byte[] tokenHash;
    public byte[] getTokenHash() { return tokenHash; }
    public void setTokenHash(byte[] value) { this.tokenHash = value; }

    /** 并发版本号 */
    @Version
    private Long version;
    public Long getVersion() { return version; }
    public void setVersion(Long value) { this.version = value; }

    /** 凭据用途NORMAL或PASSWORD_CHANGE */
    private String purpose;
    public String getPurpose() { return purpose; }
    public void setPurpose(String value) { this.purpose = value; }

    /** 认证时刻UTC（永不滑动） */
    private Instant authenticatedAt;
    public Instant getAuthenticatedAt() { return authenticatedAt; }
    public void setAuthenticatedAt(Instant value) { this.authenticatedAt = value; }

    /** 绝对到期时刻UTC（排他） */
    private Instant expiresAt;
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant value) { this.expiresAt = value; }

    /** 最近成功资格校验时刻UTC */
    private Instant lastValidatedAt;
    public Instant getLastValidatedAt() { return lastValidatedAt; }
    public void setLastValidatedAt(Instant value) { this.lastValidatedAt = value; }

    /** 签发或最近截短时的策略版本 */
    private Long policyVersion;
    public Long getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(Long value) { this.policyVersion = value; }

    /** 客户端声明设备名（非可信绑定） */
    private String deviceName;
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String value) { this.deviceName = value; }

    /** 客户端操作系统 */
    private String os;
    public String getOs() { return os; }
    public void setOs(String value) { this.os = value; }

    /** 客户端版本 */
    private String clientVersion;
    public String getClientVersion() { return clientVersion; }
    public void setClientVersion(String value) { this.clientVersion = value; }

    /** 协议版本 */
    private Integer protocolVersion;
    public Integer getProtocolVersion() { return protocolVersion; }
    public void setProtocolVersion(Integer value) { this.protocolVersion = value; }

    /** 撤销时刻UTC（不可清空） */
    private Instant revokedAt;
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant value) { this.revokedAt = value; }

    /** 撤销原因码 */
    private String revokedReason;
    public String getRevokedReason() { return revokedReason; }
    public void setRevokedReason(String value) { this.revokedReason = value; }

    /** 创建时刻UTC */
    private Instant createdAt;
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { this.createdAt = value; }

}
