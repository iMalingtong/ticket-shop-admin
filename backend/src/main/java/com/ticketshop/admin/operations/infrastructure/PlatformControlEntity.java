package com.ticketshop.admin.operations.infrastructure;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

/** 平台引导与恢复控制单例。仅用于持久化，禁止作为HTTP响应。 */
@TableName(value = "platform_control", autoResultMap = true)
public class PlatformControlEntity {
    /** 主键（正数且不超过Java Long上限） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }

    /** 是否已完成一次性引导 */
    private Boolean initialized;
    public Boolean getInitialized() { return initialized; }
    public void setInitialized(Boolean value) { this.initialized = value; }

    /** 是否处于恢复维护状态 */
    private Boolean recoveryMode;
    public Boolean getRecoveryMode() { return recoveryMode; }
    public void setRecoveryMode(Boolean value) { this.recoveryMode = value; }

    /** 并发版本号 */
    @Version
    private Long version;
    public Long getVersion() { return version; }
    public void setVersion(Long value) { this.version = value; }

    /** 更新时刻UTC */
    private Instant updatedAt;
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant value) { this.updatedAt = value; }

}
