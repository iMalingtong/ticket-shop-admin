package com.ticketshop.admin.policy.infrastructure;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

/** 客户端登录策略单例。仅用于持久化，禁止作为HTTP响应。 */
@TableName(value = "login_policy", autoResultMap = true)
public class LoginPolicyEntity {
    /** 主键（正数且不超过Java Long上限） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }

    /** 正常客户会话绝对上限小时 */
    private Integer clientMaxHours;
    public Integer getClientMaxHours() { return clientMaxHours; }
    public void setClientMaxHours(Integer value) { this.clientMaxHours = value; }

    /** 并发版本号 */
    @Version
    private Long version;
    public Long getVersion() { return version; }
    public void setVersion(Long value) { this.version = value; }

    /** 更新管理员（初始策略可空） */
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
