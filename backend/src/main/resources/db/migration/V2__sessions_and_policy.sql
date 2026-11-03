-- UTC语义；只通过Flyway执行。已发布迁移不得修改。

CREATE TABLE login_policy (
  id BIGINT UNSIGNED NOT NULL COMMENT '主键（正数且不超过Java Long上限）',
  client_max_hours INT NOT NULL DEFAULT 24 COMMENT '正常客户会话绝对上限小时',
  version BIGINT NOT NULL DEFAULT 0 COMMENT '并发版本号',
  updated_by BIGINT UNSIGNED NULL COMMENT '更新管理员（初始策略可空）',
  created_at DATETIME(6) NOT NULL COMMENT '创建时刻UTC',
  updated_at DATETIME(6) NOT NULL COMMENT '更新时刻UTC',
  PRIMARY KEY (id),
  CONSTRAINT ck_login_policy_id CHECK (id BETWEEN 1 AND 9223372036854775807),
  CONSTRAINT ck_policy_singleton CHECK (id = 1),
  CONSTRAINT ck_policy_hours CHECK (client_max_hours BETWEEN 1 AND 24 AND version >= 0),
  CONSTRAINT fk_policy_updated_by FOREIGN KEY (updated_by) REFERENCES admin_account(id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户端登录策略单例';

CREATE TABLE customer_session (
  id BIGINT UNSIGNED NOT NULL COMMENT '主键（正数且不超过Java Long上限）',
  account_id BIGINT UNSIGNED NOT NULL COMMENT '客户账号',
  sid CHAR(43) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '32字节随机会话标识Base64URL',
  jti CHAR(43) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '32字节随机JWT标识Base64URL',
  token_hash BINARY(32) NOT NULL COMMENT '完整JWT的SHA256摘要',
  version BIGINT NOT NULL DEFAULT 0 COMMENT '并发版本号',
  purpose VARCHAR(24) NOT NULL COMMENT '凭据用途NORMAL或PASSWORD_CHANGE',
  authenticated_at DATETIME(6) NOT NULL COMMENT '认证时刻UTC（永不滑动）',
  expires_at DATETIME(6) NOT NULL COMMENT '绝对到期时刻UTC（排他）',
  last_validated_at DATETIME(6) NULL COMMENT '最近成功资格校验时刻UTC',
  policy_version BIGINT NOT NULL COMMENT '签发或最近截短时的策略版本',
  device_name VARCHAR(100) NULL COMMENT '客户端声明设备名（非可信绑定）',
  os VARCHAR(16) NOT NULL COMMENT '客户端操作系统',
  client_version VARCHAR(40) NOT NULL COMMENT '客户端版本',
  protocol_version INT NOT NULL COMMENT '协议版本',
  revoked_at DATETIME(6) NULL COMMENT '撤销时刻UTC（不可清空）',
  revoked_reason VARCHAR(40) NULL COMMENT '撤销原因码',
  created_at DATETIME(6) NOT NULL COMMENT '创建时刻UTC',
  PRIMARY KEY (id),
  CONSTRAINT ck_customer_session_id CHECK (id BETWEEN 1 AND 9223372036854775807),
  UNIQUE KEY uk_customer_session_sid (sid),
  UNIQUE KEY uk_customer_session_jti (jti),
  UNIQUE KEY uk_customer_session_token (token_hash),
  CONSTRAINT ck_customer_session_purpose CHECK (purpose IN ('NORMAL','PASSWORD_CHANGE')),
  CONSTRAINT ck_customer_session_random_ids CHECK (sid REGEXP '^[A-Za-z0-9_-]{43}$' AND jti REGEXP '^[A-Za-z0-9_-]{43}$'),
  CONSTRAINT ck_customer_session_expiry CHECK (expires_at > authenticated_at AND expires_at <= DATE_ADD(authenticated_at, INTERVAL 24 HOUR) AND (purpose <> 'PASSWORD_CHANGE' OR expires_at <= DATE_ADD(authenticated_at, INTERVAL 10 MINUTE))),
  CONSTRAINT ck_customer_session_revocation CHECK ((revoked_at IS NULL AND revoked_reason IS NULL) OR (revoked_at IS NOT NULL AND revoked_reason IS NOT NULL)),
  CONSTRAINT ck_customer_session_version CHECK (version >= 0),
  KEY ix_customer_session_owner_active (account_id,revoked_at,expires_at),
  KEY ix_customer_session_expires (expires_at,id),
  CONSTRAINT ck_customer_session_os CHECK (os IN ('MACOS','WINDOWS')),
  CONSTRAINT ck_customer_session_protocol CHECK (protocol_version >= 1 AND policy_version >= 0),
  KEY ix_customer_session_validated (last_validated_at,id),
  CONSTRAINT fk_customer_session_account_id FOREIGN KEY (account_id) REFERENCES customer_account(id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户JWT会话事实';

CREATE TABLE admin_session (
  id BIGINT UNSIGNED NOT NULL COMMENT '主键（正数且不超过Java Long上限）',
  admin_id BIGINT UNSIGNED NOT NULL COMMENT '管理员账号',
  sid CHAR(43) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '32字节随机会话标识Base64URL',
  jti CHAR(43) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '32字节随机JWT标识Base64URL',
  token_hash BINARY(32) NOT NULL COMMENT '完整JWT的SHA256摘要',
  version BIGINT NOT NULL DEFAULT 0 COMMENT '并发版本号',
  purpose VARCHAR(24) NOT NULL COMMENT '凭据用途NORMAL或PASSWORD_CHANGE',
  authenticated_at DATETIME(6) NOT NULL COMMENT '认证时刻UTC（永不滑动）',
  expires_at DATETIME(6) NOT NULL COMMENT '绝对到期时刻UTC（排他）',
  csrf_hash BINARY(32) NOT NULL COMMENT 'CSRF随机值摘要',
  last_activity_at DATETIME(6) NOT NULL COMMENT '最近真实活动时刻UTC',
  revoked_at DATETIME(6) NULL COMMENT '撤销时刻UTC（不可清空）',
  revoked_reason VARCHAR(40) NULL COMMENT '撤销原因码',
  created_at DATETIME(6) NOT NULL COMMENT '创建时刻UTC',
  PRIMARY KEY (id),
  CONSTRAINT ck_admin_session_id CHECK (id BETWEEN 1 AND 9223372036854775807),
  UNIQUE KEY uk_admin_session_sid (sid),
  UNIQUE KEY uk_admin_session_jti (jti),
  UNIQUE KEY uk_admin_session_token (token_hash),
  CONSTRAINT ck_admin_session_purpose CHECK (purpose IN ('NORMAL','PASSWORD_CHANGE')),
  CONSTRAINT ck_admin_session_random_ids CHECK (sid REGEXP '^[A-Za-z0-9_-]{43}$' AND jti REGEXP '^[A-Za-z0-9_-]{43}$'),
  CONSTRAINT ck_admin_session_expiry CHECK (expires_at > authenticated_at AND expires_at <= DATE_ADD(authenticated_at, INTERVAL 8 HOUR) AND (purpose <> 'PASSWORD_CHANGE' OR expires_at <= DATE_ADD(authenticated_at, INTERVAL 10 MINUTE))),
  CONSTRAINT ck_admin_session_revocation CHECK ((revoked_at IS NULL AND revoked_reason IS NULL) OR (revoked_at IS NOT NULL AND revoked_reason IS NOT NULL)),
  CONSTRAINT ck_admin_session_version CHECK (version >= 0),
  KEY ix_admin_session_owner_active (admin_id,revoked_at,expires_at),
  KEY ix_admin_session_expires (expires_at,id),
  CONSTRAINT fk_admin_session_admin_id FOREIGN KEY (admin_id) REFERENCES admin_account(id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理员JWT及CSRF会话事实';

CREATE TABLE admin_pre_auth (
  id BIGINT UNSIGNED NOT NULL COMMENT '主键（正数且不超过Java Long上限）',
  token_hash BINARY(32) NOT NULL COMMENT '预认证随机Cookie摘要',
  csrf_hash BINARY(32) NOT NULL COMMENT 'CSRF随机值摘要',
  created_at DATETIME(6) NOT NULL COMMENT '创建时刻UTC',
  expires_at DATETIME(6) NOT NULL COMMENT '预认证到期时刻UTC',
  consumed_at DATETIME(6) NULL COMMENT '消费时刻UTC',
  PRIMARY KEY (id),
  CONSTRAINT ck_admin_pre_auth_id CHECK (id BETWEEN 1 AND 9223372036854775807),
  UNIQUE KEY uk_pre_auth_token (token_hash),
  KEY ix_pre_auth_expires (expires_at,id),
  CONSTRAINT ck_pre_auth_expiry CHECK (expires_at > created_at AND expires_at <= DATE_ADD(created_at, INTERVAL 10 MINUTE))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='匿名CSRF预认证绑定';

INSERT INTO login_policy (id,client_max_hours,version,updated_by,created_at,updated_at) VALUES (1,24,0,NULL,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6));
