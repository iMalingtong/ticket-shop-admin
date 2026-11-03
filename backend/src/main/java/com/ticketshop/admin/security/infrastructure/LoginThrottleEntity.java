package com.ticketshop.admin.security.infrastructure;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

/** 持久化登录限流计数。仅用于持久化，禁止作为HTTP响应。 */
@TableName(value = "login_throttle", autoResultMap = true)
public class LoginThrottleEntity {
    /** 主键（正数且不超过Java Long上限） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }

    /** 身份域 */
    private String realm;
    public String getRealm() { return realm; }
    public void setRealm(String value) { this.realm = value; }

    /** 账号或来源维度 */
    private String dimension;
    public String getDimension() { return dimension; }
    public void setDimension(String value) { this.dimension = value; }

    /** 规范账号或可信来源HMAC摘要 */
    private byte[] keyDigest;
    public byte[] getKeyDigest() { return keyDigest; }
    public void setKeyDigest(byte[] value) { this.keyDigest = value; }

    /** 窗口内失败数 */
    private Integer failureCount;
    public Integer getFailureCount() { return failureCount; }
    public void setFailureCount(Integer value) { this.failureCount = value; }

    /** 计数窗口开始UTC */
    private Instant windowStartedAt;
    public Instant getWindowStartedAt() { return windowStartedAt; }
    public void setWindowStartedAt(Instant value) { this.windowStartedAt = value; }

    /** 临时限制截止UTC */
    private Instant blockedUntil;
    public Instant getBlockedUntil() { return blockedUntil; }
    public void setBlockedUntil(Instant value) { this.blockedUntil = value; }

    /** 更新时刻UTC */
    private Instant updatedAt;
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant value) { this.updatedAt = value; }

    /** 并发版本号 */
    @Version
    private Long version;
    public Long getVersion() { return version; }
    public void setVersion(Long value) { this.version = value; }

}
