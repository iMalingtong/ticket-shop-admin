-- UTC语义；只通过Flyway执行。已发布迁移不得修改。

CREATE TABLE audit_event (
  id BIGINT UNSIGNED NOT NULL COMMENT '主键（正数且不超过Java Long上限）',
  actor_type VARCHAR(24) NOT NULL COMMENT '操作者身份域',
  actor_id BIGINT UNSIGNED NULL COMMENT '操作者多态标识',
  actor_name_snapshot VARCHAR(64) NOT NULL COMMENT '操作者名称快照',
  target_type VARCHAR(40) NOT NULL COMMENT '目标类型',
  target_id BIGINT UNSIGNED NULL COMMENT '目标多态标识',
  action VARCHAR(64) NOT NULL COMMENT '操作代码',
  before_values JSON NULL COMMENT '脱敏前值白名单',
  after_values JSON NULL COMMENT '脱敏后值白名单',
  reason VARCHAR(500) NULL COMMENT '操作原因',
  result VARCHAR(16) NOT NULL COMMENT '操作结果',
  error_code VARCHAR(64) NULL COMMENT '失败错误代码',
  request_id VARCHAR(64) NOT NULL COMMENT '服务器请求标识',
  occurred_at DATETIME(6) NOT NULL COMMENT '发生时刻UTC',
  PRIMARY KEY (id),
  CONSTRAINT ck_audit_event_id CHECK (id BETWEEN 1 AND 9223372036854775807),
  CONSTRAINT ck_audit_actor CHECK (actor_type IN ('ADMIN','SYSTEM')),
  CONSTRAINT ck_audit_result CHECK (result IN ('SUCCESS','FAILURE')),
  KEY ix_audit_time (occurred_at,id),
  KEY ix_audit_target (target_type,target_id,occurred_at),
  KEY ix_audit_actor (actor_id,occurred_at),
  KEY ix_audit_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理操作审计（只追加）';

CREATE TABLE login_security_event (
  id BIGINT UNSIGNED NOT NULL COMMENT '主键（正数且不超过Java Long上限）',
  identity_type VARCHAR(16) NOT NULL COMMENT '身份域',
  account_id BIGINT UNSIGNED NULL COMMENT '已确认账号多态标识',
  account_key_digest BINARY(32) NOT NULL COMMENT '规范账号HMAC摘要',
  event_type VARCHAR(40) NOT NULL COMMENT '事件代码',
  result VARCHAR(16) NOT NULL COMMENT '结果',
  code VARCHAR(64) NOT NULL COMMENT '原因代码',
  source_ip VARBINARY(16) NULL COMMENT '必要来源IP二进制',
  client_version VARCHAR(40) NULL COMMENT '客户端版本',
  request_id VARCHAR(64) NOT NULL COMMENT '服务器请求标识',
  occurred_at DATETIME(6) NOT NULL COMMENT '发生时刻UTC',
  PRIMARY KEY (id),
  CONSTRAINT ck_login_security_event_id CHECK (id BETWEEN 1 AND 9223372036854775807),
  CONSTRAINT ck_security_identity CHECK (identity_type IN ('CUSTOMER','ADMIN')),
  CONSTRAINT ck_security_result CHECK (result IN ('SUCCESS','FAILURE','THROTTLED')),
  KEY ix_security_time (occurred_at,id),
  KEY ix_security_account (identity_type,account_id,occurred_at),
  KEY ix_security_type (event_type,occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录安全事件（只追加）';

CREATE TABLE login_throttle (
  id BIGINT UNSIGNED NOT NULL COMMENT '主键（正数且不超过Java Long上限）',
  realm VARCHAR(16) NOT NULL COMMENT '身份域',
  dimension VARCHAR(16) NOT NULL COMMENT '账号或来源维度',
  key_digest BINARY(32) NOT NULL COMMENT '规范账号或可信来源HMAC摘要',
  failure_count INT NOT NULL DEFAULT 0 COMMENT '窗口内失败数',
  window_started_at DATETIME(6) NOT NULL COMMENT '计数窗口开始UTC',
  blocked_until DATETIME(6) NULL COMMENT '临时限制截止UTC',
  updated_at DATETIME(6) NOT NULL COMMENT '更新时刻UTC',
  version BIGINT NOT NULL DEFAULT 0 COMMENT '并发版本号',
  PRIMARY KEY (id),
  CONSTRAINT ck_login_throttle_id CHECK (id BETWEEN 1 AND 9223372036854775807),
  CONSTRAINT ck_throttle_scope CHECK (realm IN ('CUSTOMER','ADMIN') AND dimension IN ('ACCOUNT','SOURCE')),
  CONSTRAINT ck_throttle_count CHECK (failure_count >= 0 AND version >= 0),
  UNIQUE KEY uk_throttle_dimension (realm,dimension,key_digest),
  KEY ix_throttle_blocked (blocked_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='持久化登录限流计数';

CREATE TABLE mutation_request (
  id BIGINT UNSIGNED NOT NULL COMMENT '主键（正数且不超过Java Long上限）',
  actor_type VARCHAR(24) NOT NULL COMMENT '操作者身份域',
  actor_id BIGINT UNSIGNED NOT NULL COMMENT '操作者标识',
  idempotency_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'UUID幂等键',
  route VARCHAR(160) NOT NULL COMMENT '规范化请求路由（含方法）',
  target_id BIGINT UNSIGNED NULL COMMENT '目标对象标识',
  fingerprint BINARY(32) NOT NULL COMMENT '含密码字段使用HMAC的请求指纹',
  state VARCHAR(16) NOT NULL COMMENT '处理状态',
  result_code INT NULL COMMENT '提交结果HTTP状态',
  resource_id BIGINT UNSIGNED NULL COMMENT '结果资源标识',
  result_snapshot JSON NULL COMMENT '可重放结果业务白名单（禁止秘密）',
  request_id VARCHAR(64) NOT NULL COMMENT '首次处理的服务器请求标识',
  created_at DATETIME(6) NOT NULL COMMENT '创建时刻UTC',
  expires_at DATETIME(6) NOT NULL COMMENT '记录过期时刻UTC',
  PRIMARY KEY (id),
  CONSTRAINT ck_mutation_request_id CHECK (id BETWEEN 1 AND 9223372036854775807),
  CONSTRAINT ck_mutation_actor CHECK (actor_type = 'ADMIN'),
  CONSTRAINT ck_mutation_state CHECK (state IN ('PROCESSING','COMMITTED')),
  CONSTRAINT ck_mutation_expiry CHECK (expires_at > created_at),
  UNIQUE KEY uk_mutation_actor_key (actor_type,actor_id,idempotency_key),
  KEY ix_mutation_expires (expires_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理写请求幂等记录（不保存秘密）';
