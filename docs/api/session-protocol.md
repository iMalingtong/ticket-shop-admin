# 会话、JWT 与 Redis 协议（P2冻结，P3已实现）

P3已实现13项认证操作，按本协议验证。经用户明确授权仅创建并读取本项目新建开发密钥，不读取已有外部密钥。生产/桌面集成仍未实施。

## JWT

| 项目 | 约定 |
|---|---|
| 保护头 | alg固定RS256，typ=JWT；kid为受控本地密钥集合中的1～64字符ASCII标识；不处理远程jku/x5u，不按令牌声明动态获取密钥 |
| issuer | `urn:ticket-shop-admin:{environment}`，environment仅development/test/production，部署间不得混用 |
| audience | 客户`ticket-shop-client`，管理员`ticket-shop-admin`；只接受所属身份链的单一audience |
| principalType | CUSTOMER/ADMIN，必须匹配路径、audience及会话表 |
| sub | 正十进制账号ID字符串，范围1～9223372036854775807 |
| sid/jti | 各自CSPRNG32字节，无填充Base64URL，43字符；表内唯一且区分大小写；不把它们当作免验签凭据 |
| purpose | NORMAL/PASSWORD_CHANGE；受限凭据只允许本身份域首次改密及退出 |
| iat/exp | Unix整数秒；iat固定认证时刻，exp向下取整至不晚于数据库上界，不给到期宽限；拒绝iat超服务器30秒、exp≤iat或缺失声明；不发refresh token |
| 数据 | 不含密码、CSRF、账号状态、使用期限、权限及客户业务信息；权限每次来自服务器权威数据 |

客户NORMAL数据库上界=authenticatedAt＋当次1～24h策略；管理NORMAL最长8h且另验30min空闲；PASSWORD_CHANGE最长10min。管理员改密、冻结、策略截短等可让数据库上界早于原JWT exp，必须按数据库拒绝，不能重签或恢复旧上界。客户资格另验账号期限，不能以JWT的有效期替代使用资格。

验签或格式错误、错域/错kid/错算法/缺声明返回401 SESSION_INVALID；明确会话到期为SESSION_EXPIRED、已撤销为SESSION_REVOKED；首次改密凭据无效为PASSWORD_CHANGE_TOKEN_INVALID。密码登录失败统一INVALID_CREDENTIALS。只在密码或身份确认之后返回账号全部适用拒绝原因。

## Cookie、CSRF 与内存

- 管理会话`tsa_admin_session`：JWT、host-only、HttpOnly、Path=/api/v1/admin、SameSite=Strict，生产Secure，本机development才允许非Secure。
- 匿名绑定`tsa_admin_pre_auth`：独立随机32字节Cookie，不是正常身份；仅DB保存SHA256，最长10min，登录消费并清Cookie。
- CSRF随机32字节，页面仅内存保存；数据库保存SHA256，写请求通过X-CSRF-Token提交并验证允许Origin。GET csrf无Origin时检查同源Fetch Metadata/Referer，不能因浏览器不提供Origin就静默关闭来源检查。
- csrf获取会轮换摘要；已有正常/受限管理会话绑定该会话，没有时建立预认证。登录与权限变化轮换会话与CSRF。页面刷新先me，再csrf；恢复过程中不发管理写请求。

## Redis键与值

统一前缀`ticket-shop-admin:{environment}:`；环境必须显式，禁止用户名、JWT原文、密码或来源IP出现在键中。逻辑库默认0，前缀不是生产网络/ACL安全边界。

| 后缀 | 最小值字段 | TTL |
|---|---|---|
| session:{CUSTOMER或ADMIN}:{sid} | schemaVersion=1、sessionId/accountId字符串、sessionVersion/accountVersion/passwordVersion字符串、purpose、tokenHash十六进制摘要、expiresAt；客户lastValidatedAt或管理lastActivityAt（适用时） | 向下取整，不晚于JWT exp、数据库实际expiresAt；客户还受validUntil约束，管理NORMAL还受lastActivityAt＋30min约束。剩余≤0不写缓存 |
| pre-auth:ADMIN:{tokenHashHex} | schemaVersion=1、preAuthId、expiresAt；不存CSRF原值 | 不晚于数据库预认证到期，最长600秒；消费后删除 |
| login-throttle:{realm}:{dimension}:{keyDigestHex} | schemaVersion=1、failureCount、windowStartedAt、blockedUntil、version | 不晚于当前窗口或限制结束的较晚者；键缺失必须读数据库计数，不能清零限制 |

会话缓存只作辅助摘要。固定顺序：JWT约束→Redis可达与读取→MySQL权威会话、账号、策略检查→必要缓存修复→成功。缺失、过旧、schemaVersion未知或摘要冲突只能根据数据库校正；已撤销/过期/账号拒绝均不重建可用会话。需要修复缓存而写入失败时，认证返回503，不能返回成功。

资格成功必须检查数据库和Redis均可用；状态修改在MySQL先提交，再同步缓存，后者失败返回已提交结果＋cacheSyncStatus=PENDING。PENDING不是“操作失败”，也不放行旧会话。任何读取旧缓存的请求仍要核验数据库，因此缓存恢复不复活撤销记录。不使用FLUSHALL/FLUSHDB。

## 时限与错误

外部单次请求上限10秒。Redis连接/命令、Hikari取连接及JDBC连接/读取上限各2秒；RequestBudget使用单调时钟记录10秒总预算，并预留2秒用于提交/回滚/响应收尾，剩余预算约束事务及每次SQL执行，Redis调用前不足预算直接503；没有自动写重试。写事务发生超时或连接异常时返回503，并由幂等查询判断是否已提交，不能自动重发写请求。

SQL、Redis、无效协议响应、超时或网络失败均不得当作认证成功。Redis或数据库不可用映射503 SERVICE_UNAVAILABLE；维护状态503 MAINTENANCE。客户端只可使用PRD已确认的当前运行实例剩余网络窗口，不形成新的离线授权。

协议版本首版支持1：非法格式400 PROTOCOL_INVALID，格式有效但不支持426 PROTOCOL_UNSUPPORTED；用户已确认P3只强制协议1，最低软件版本配置默认留空，待桌面交接时确定并启用；不读取桌面工程推测版本号。错误、权限与资格字段的机器定义见[OpenAPI](openapi.yaml)。


P3运行细节：Argon2id参数为19MiB/t=2/p=1，DelegatingPasswordEncoder算法标识argon2id，依赖Bouncy Castle1.86；最多4个并行昂贵密码计算。未知账号执行固定虚拟摘要验证；标准来源仅取直连remoteAddr，不信任任意转发头。账号5次/15分钟、来源50次/15分钟，限制15分钟；CSRF独立来源计数60次/15分钟，避免匿名绑定无限创建。计数初始化在小事务中完成，登录计数事务统一SOURCE→ACCOUNT→控制→策略→账号→会话，成功只清账号失败计数。受限/正常用途和两身份域均由JWT与DB事实共同验证，未实现的管理路由默认关闭。
