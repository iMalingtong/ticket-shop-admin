# P3 身份认证与登录闭环验收

- 日期：2026-11-03；范围：前后端分离的身份认证，不含P4客户管理。
- 本机开发签名文件的创建/读取已获明确授权；最低客户端软件版本按用户确认暂留空，仅强制协议1。
- 共享开发服务已启用真实认证接口；没有默认账号、生产配置或桌面工程修改。

## 交付与需求映射

| 步骤 | 实现与验收 |
|---|---|
| B3-01 | Credentials使用Spring DelegatingPasswordEncoder＋Argon2id；19MiB/t=2/p=1、盐16字节/摘要32字节、最多4个并行昂贵计算；未知账号虚拟摘要；12–128 Unicode码点，不trim/截断，拒绝未配对代理项 |
| B3-02 | --bootstrap-admin仅交互终端隐藏输入，无HTTP入口、密码参数或默认密码；控制行锁、超管、初始化审计、initialized同事务；真实PTY首次成功/第二次拒绝且未回显密码 |
| B3-03 | 独立admin/client SecurityFilterChain允许已实现13项认证操作，其他管理路由默认拒绝；Controller身份入口调用固定Realm的JWT＋DB验证，RS256/kid/typ/issuer/单一audience/claims严格检查；拒绝远程jku/x5u/未知crit |
| B3-04 | JWT→Redis可达→MySQL控制/策略/账号/会话权威检查→缓存校正；缓存缺失可重建，旧缓存不可覆盖撤销；读取不滑动绝对期限，改密先提交再删全部账号会话摘要；失败返回PENDING |
| B3-05 | client的login/first-password/validate/account/password/logout均实现；受限JWT不返回资格，改密消费/撤销后必须重新登录，普通会话上限由当次策略决定，重复退出幂等 |
| B3-06 | admin的csrf/login/first-password/me/activity/password/logout均实现；JWT仅HttpOnly Cookie，host-only/Strict/固定Path，生产Secure及HTTPS来源；GET csrf来源与写请求Origin/CSRF均校验 |
| B3-07 | MySQL账号5次/来源50次失败、15分钟窗口和限制；成功只清账号计数；不存在账号同样计数，HMAC隔离两身份域；直连remoteAddr不信任伪造转发头；CSRF独立来源60次/15分钟 |
| B3-08 | 服务端生成requestId，统一错误/no-store；16KiB请求体、密码并发限制、单调10秒总预算并保留2秒收尾；事务/SQL按剩余预算，JDBC连接/读取及Redis各2秒；无自动写重试，日志只输出安全错误类型/SQL状态码 |
| F3-01 | 真实登录、可显示/隐藏/粘贴密码、提交禁重；错密码统一提示、保留账号；没有Mock或持久化密码 |
| F3-02 | 首次改密专页与刷新恢复；受限身份进入/account仍重定向改密页；成功清秘密并返回登录 |
| F3-03 | me恢复摘要、轮换内存CSRF、默认拒绝路由策略、真实退出；登录/改密/退出均有浏览器实跑，网络失败退出不假报成功 |
| F3-04 | 仅可信且可见页的键鼠活动触发activity，至多每分钟一次，无轮询续命；失效事件集中清身份/重定向，错当前密码不误清会话 |

Argon2依赖Bouncy Castle1.86，具体编码器依据[Spring Security密码存储文档](https://docs.spring.io/spring-security/reference/6.5/features/authentication/password-storage.html)；[Bouncy Castle官方发布页](https://www.bouncycastle.org/download/bouncy-castle-java/)及[Nimbus RSA JWT示例](https://connect2id.com/products/nimbus-jose-jwt/examples/jwt-with-rsa-signature)为实现核对来源。开发机10次验证均值约20ms，仅记录本机基准，生产参数及容量仍需P7压测。

## 验证证据

完整后端verify包含11项单元与25项真实容器集成测试，共36项；最终结果以backend/target/surefire-reports、failsafe-reports及runtime/p3-backend.log为准。

- 客户临时密码→受限JWT→首次改密→新密码登录→校验→真正停止/重启独立Java HTTP进程→旧JWT保持原authenticatedAt/expiresAt→退出。重启使用相同注入密钥，删除自己的缓存后仍按数据库原会话恢复。
- 管理员初始化→受限Cookie→首次改密→新密码登录→me/CSRF恢复→本人改密→旧会话拒绝→重新登录→退出及重复退出。
- 可控时钟检查客户24h、管理8h/30min、首次改密10min；me只读不续空闲，真实activity不突破8h。
- 并发10次错误登录恰好5次401/5次429；清理自己的Redis限流缓存仍不能清零数据库计数；伪造X-Forwarded-For不能换来源。
- 已验证密码后才返回账号全部阻碍；无效算法、kid、typ、issuer、audience、缺声明、未来iat、篡改、跨域令牌均拒绝。
- 隔离MySQL暂停时返回503且及时恢复；隔离Redis停止时不能登录/授权。认证后停止Redis再改密：DB已提交、会话已撤销，返回PENDING。
- 临时CHECK拒绝审计写入时，密码与会话撤销一并回滚；移除仅测试库内的CHECK后才能成功。
- 独立8849前端＋随机端口后端的Playwright真实认证流程1项；常规环境/404/主题浏览器回归3项。错误密码提示、受限路由、刷新恢复、本人改密、退出均实跑；HttpOnly Cookie不可由脚本读取，浏览器存储不含账号/密码/JWT/CSRF。已回看合成管理员账号页截图。
- 前端20项单元测试，类型检查、只读lint、build、OpenAPI/生成类型漂移校验通过；契约仍为36路径/40操作，无新增业务API。

CI已配置隔离真实认证浏览器流程，远端CI未运行。测试不停止共享服务、不写真实客户数据；所有合成账号仅在Testcontainers库中，测试库随容器销毁。

## 实际故障与修复

1. 使用注解激活test profile时不一定存在spring.profiles.active文本属性；Redis前缀改从已验证的活跃环境派生。
2. 同一事务先UPSERT再FOR UPDATE的并发限流出现MySQL1213死锁。计数行初始化改为独立短事务，计数事务统一SOURCE→ACCOUNT顺序；来源已被限制时不再为任意新账号创建计数行。未引入自动写重试。
3. 单靠JDBC查询超时不能限制数据库网络失联；补上连接/读取超时与请求剩余预算，隔离暂停用例验证503。固定保留收尾预算，避免把每个局部超时无限累计。
4. Java HttpClient探测Vite时HTTP2升级可能等待；测试探测固定HTTP1.1并设显式超时。CLI用真实PTY，不用管道密码绕过隐藏输入约束。

## 本机状态与操作

P3开发实例已由独立runtime JAR快照运行，后端18080及前端8848代理readiness均200 UP。匿名me为结构化401 SESSION_INVALID；允许来源的csrf为200，Cookie具有HttpOnly/Strict/固定Path。

共享专用库回读：admin_account=0、customer_account=0、initialized=0，Flyway1/2/3全部成功。只创建开发签名配置，未替用户设置任何管理员密码。密码及JWT/HMAC原值没有进入输出、仓库、前端构建或日志。

使用者需在自己的交互终端初始化正式开发管理员，具体命令见[本机手册](../runbooks/local-development.md#开发签名与离线管理员引导)；无需把密码发到聊天中。

## 安全复核与停止点

复核覆盖身份域/用途隔离、受限改密、密码边界、来源/CSRF、会话撤销、缓存权威性、限流并发、错误/日志、开发密钥权限与引导原子性；本轮发现的死锁和故障超时问题已修复并回归，没有剩余可触发的放行问题。此结论限上述本机路径，不代表生产渗透、负载容量或桌面平台验收。

停在P3；P4客户管理、P5策略/权限管理、P6后台业务页面、P7全链路验收、P8运行交付/协议交接、P9桌面接入及P10生产部署均未开始。正式管理员密码由使用者在终端设置，生产密钥/服务器/HTTPS与最低桌面版本在对应阶段落实。
