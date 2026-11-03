package com.ticketshop.admin.authentication.infrastructure;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

/** 匿名CSRF预认证绑定。仅用于持久化，禁止作为HTTP响应。 */
@TableName(value = "admin_pre_auth", autoResultMap = true)
public class AdminPreAuthEntity {
    /** 主键（正数且不超过Java Long上限） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }

    /** 预认证随机Cookie摘要 */
    private byte[] tokenHash;
    public byte[] getTokenHash() { return tokenHash; }
    public void setTokenHash(byte[] value) { this.tokenHash = value; }

    /** CSRF随机值摘要 */
    private byte[] csrfHash;
    public byte[] getCsrfHash() { return csrfHash; }
    public void setCsrfHash(byte[] value) { this.csrfHash = value; }

    /** 创建时刻UTC */
    private Instant createdAt;
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { this.createdAt = value; }

    /** 预认证到期时刻UTC */
    private Instant expiresAt;
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant value) { this.expiresAt = value; }

    /** 消费时刻UTC */
    private Instant consumedAt;
    public Instant getConsumedAt() { return consumedAt; }
    public void setConsumedAt(Instant value) { this.consumedAt = value; }

}
