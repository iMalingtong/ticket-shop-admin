package com.ticketshop.admin.customer.infrastructure;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

/** 客户账号。仅用于持久化，禁止作为HTTP响应。 */
@TableName(value = "customer_account", autoResultMap = true)
public class CustomerAccountEntity {
    /** 主键（正数且不超过Java Long上限） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }

    /** 登录名（保留大小写） */
    private String username;
    public String getUsername() { return username; }
    public void setUsername(String value) { this.username = value; }

    /** 规范化小写唯一登录名 */
    private String usernameKey;
    public String getUsernameKey() { return usernameKey; }
    public void setUsernameKey(String value) { this.usernameKey = value; }

    /** 显示名称 */
    private String displayName;
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String value) { this.displayName = value; }

    /** 带算法标识的密码摘要 */
    private String passwordHash;
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String value) { this.passwordHash = value; }

    /** 密码摘要版本 */
    private Long passwordVersion;
    public Long getPasswordVersion() { return passwordVersion; }
    public void setPasswordVersion(Long value) { this.passwordVersion = value; }

    /** 账号状态（期限状态另行计算） */
    private String status;
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }

    /** 生效时刻（包含）UTC */
    private Instant validFrom;
    public Instant getValidFrom() { return validFrom; }
    public void setValidFrom(Instant value) { this.validFrom = value; }

    /** 到期时刻（不包含）UTC */
    private Instant validUntil;
    public Instant getValidUntil() { return validUntil; }
    public void setValidUntil(Instant value) { this.validUntil = value; }

    /** 必须首次改密 */
    private Boolean mustChangePassword;
    public Boolean getMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(Boolean value) { this.mustChangePassword = value; }

    /** 备注 */
    private String remark;
    public String getRemark() { return remark; }
    public void setRemark(String value) { this.remark = value; }

    /** 最近正常登录时刻UTC */
    private Instant lastLoginAt;
    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant value) { this.lastLoginAt = value; }

    /** 最近成功资格校验时刻UTC */
    private Instant lastValidatedAt;
    public Instant getLastValidatedAt() { return lastValidatedAt; }
    public void setLastValidatedAt(Instant value) { this.lastValidatedAt = value; }

    /** 并发版本号 */
    @Version
    private Long version;
    public Long getVersion() { return version; }
    public void setVersion(Long value) { this.version = value; }

    /** 创建管理员 */
    private Long createdBy;
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long value) { this.createdBy = value; }

    /** 更新管理员 */
    private Long updatedBy;
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long value) { this.updatedBy = value; }

    /** 创建时刻UTC */
    private Instant createdAt;
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { this.createdAt = value; }

    /** 更新时刻UTC */
    private Instant updatedAt;
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant value) { this.updatedAt = value; }

}
