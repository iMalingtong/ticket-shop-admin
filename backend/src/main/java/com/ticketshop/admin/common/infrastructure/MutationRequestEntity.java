package com.ticketshop.admin.common.infrastructure;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

/** 管理写请求幂等记录（不保存秘密）。仅用于持久化，禁止作为HTTP响应。 */
@TableName(value = "mutation_request", autoResultMap = true)
public class MutationRequestEntity {
    /** 主键（正数且不超过Java Long上限） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }

    /** 操作者身份域 */
    private String actorType;
    public String getActorType() { return actorType; }
    public void setActorType(String value) { this.actorType = value; }

    /** 操作者标识 */
    private Long actorId;
    public Long getActorId() { return actorId; }
    public void setActorId(Long value) { this.actorId = value; }

    /** UUID幂等键 */
    private String idempotencyKey;
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String value) { this.idempotencyKey = value; }

    /** 规范化请求路由（含方法） */
    private String route;
    public String getRoute() { return route; }
    public void setRoute(String value) { this.route = value; }

    /** 目标对象标识 */
    private Long targetId;
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long value) { this.targetId = value; }

    /** 含密码字段使用HMAC的请求指纹 */
    private byte[] fingerprint;
    public byte[] getFingerprint() { return fingerprint; }
    public void setFingerprint(byte[] value) { this.fingerprint = value; }

    /** 处理状态 */
    private String state;
    public String getState() { return state; }
    public void setState(String value) { this.state = value; }

    /** 提交结果HTTP状态 */
    private Integer resultCode;
    public Integer getResultCode() { return resultCode; }
    public void setResultCode(Integer value) { this.resultCode = value; }

    /** 结果资源标识 */
    private Long resourceId;
    public Long getResourceId() { return resourceId; }
    public void setResourceId(Long value) { this.resourceId = value; }

    /** 可重放结果业务白名单（禁止秘密） */
    private String resultSnapshot;
    public String getResultSnapshot() { return resultSnapshot; }
    public void setResultSnapshot(String value) { this.resultSnapshot = value; }

    /** 首次处理的服务器请求标识 */
    private String requestId;
    public String getRequestId() { return requestId; }
    public void setRequestId(String value) { this.requestId = value; }

    /** 创建时刻UTC */
    private Instant createdAt;
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { this.createdAt = value; }

    /** 记录过期时刻UTC */
    private Instant expiresAt;
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant value) { this.expiresAt = value; }

}
