# ADR-0001：MyBatis-Plus、MySQL 8.0 与 JWT＋Redis 会话控制

- 状态：已采纳（选型经用户确认，以下实现约束待开发验证）
- 日期：2026-11-02
- 关联文档：[PRD](../PRD.md)、[开发说明书](../DEVELOPMENT_SPEC.md)、[阶段流程](../DEVELOPMENT_PLAN.md)

## 背景与约束

用户确认Java 21、Spring Boot 3.5系列、MyBatis-Plus＋Flyway，复用Docker中的mysql:8.0、redis:7-alpine；同仓库backend/frontend独立构建发布，先完成本机功能验收。冻结、停用、改密、重置和撤销不能等待JWT自然过期；服务重启不能丢失撤销记录。暂时没有生产服务器，账号规模尚未确定。

原开发说明书中的Spring JDBC、MySQL 8.4及不引入JWT/Redis的建议由本决策取代。原来已经确认的24小时绝对登录上限、5分钟校验、10分钟网络窗口、后台8小时/空闲30分钟和首次改密要求保持有效。

## 决策

### 持久化与工程

- 同仓库 `backend/`、`frontend/`；Java产物不打包后台dist，后台不包含服务端密钥。通过OpenAPI协作，生产规划同源代理，不要求两个仓库或两个域名。
- MyBatis-Plus提供普通CRUD与分页；并发状态修改、SELECT FOR UPDATE、撤销、策略缩短、统计与审计使用明确Mapper方法/SQL。所有用户输入参数绑定，排序白名单，不用通用saveOrUpdate代替领域操作。
- Flyway是唯一结构迁移入口；实际MySQL必须至少8.0.16，使CHECK约束执行。精确MySQL/Redis补丁、MyBatis-Plus和Flyway版本在工程阶段实测锁定。

### JWT与会话事实

- 使用成熟JOSE实现，首版RS256，算法白名单、固定可信issuer、不同的admin/client audience；校验签名、exp、nbf（若有）、iat、sid、jti、sub、purpose和principalType。不信任token中的任意远程key地址或声明算法。
- JWT不存密码、账号状态、使用期限、来源路径或设备敏感信息；角色声明（若有）不作为当前权限事实。私钥安全注入；kid仅从受控本地key集合选择，不读取现有敏感文件。
- 数据库保存sid、jti、完整JWT摘要、用途、绝对期限、撤销、版本等，Redis保存对应会话摘要；均不保存JWT原文。NORMAL桌面JWT的exp不晚于数据库绝对上限，PASSWORD_CHANGE最多10分钟，后台NORMAL最多8小时；数据库上界更早时按更早值拒绝。
- 不提供refresh token，不靠重新签发延长登录。策略缩短时即使JWT原exp较晚，数据库较早上界仍有效；放宽不恢复旧上界。JWT秒精度取不晚于数据库期限的值，不能向后取整增加授权。
- 后台JWT置于HttpOnly Cookie，前端脚本不可读取，仍执行CSRF和Origin校验；桌面JWT仅在Java侧持有，以Bearer发送。匿名CSRF绑定用独立随机预认证Cookie，不复用正常JWT。

### 每次认证及Redis一致性

首版以正确性和可复核为先，不将Redis作为唯一授权事实：

1. 验JWT签名和身份域，错误即拒绝。
2. 访问本项目Redis会话键；Redis连接失败/超时返回503，不退回仅验JWT。键缺失不直接判为可用。
3. 读取MySQL账号、会话与策略的权威事实，按应用事务/锁规则检查状态、期限、撤销、摘要及用途；数据库失败同样503。
4. Redis缺失/版本过旧时仅依据数据库有效记录重建，TTL不超过数据库实际剩余期限；Redis项与数据库冲突时数据库优先。数据库已撤销则拒绝并尽力删除缓存，不能由旧Redis项恢复。
5. 需要写回Redis时写入失败不得返回认证成功。并发的旧缓存写入即使发生，下一次认证仍须读取数据库，因此不能造成放行。

Redis用于会话摘要、有效期快速过滤和必要的限流辅助；首版不减少权威数据库检查。数据库限流计数和撤销仍持久化，缓存丢失或Redis恢复不能清零安全限制。实际规模确定后再评估减少数据库读的方案，不能直接删掉权威检查。

### 状态修改与故障语义

- 冻结/停用/改密/撤销/策略缩短先在MySQL事务中完成状态、会话和审计；数据库提交即为服务端变更成功。提交后刷新或删除相关Redis项。
- 不将MySQL事务与Redis写入伪装成原子提交。提交后缓存更新失败时不能声称数据库回滚；返回已提交结果及 `cacheSyncStatus=PENDING`，记录脱敏告警。此时旧JWT仍受每次MySQL检查保护；Redis整体不可用时认证503。
- 首版不额外引入跨存储分布式事务、消息队列或outbox：懒加载校正加每次数据库检查足以保持撤销语义。恢复后通过权威校验和项目会话重建修复缓存。
- 退出需幂等；远程撤销提交成功才算远端成功。桌面断网清本机凭据与远端撤销是两个结果。Redis503对桌面属于校验不可用，仍只允许PRD规定的当前运行实例剩余窗口，不是离线授权。
- Redis与MySQL的时限必须小于单次认证总请求10秒预算，连接与命令超时在阶段测试中配置并验证，不允许无界重试。

### 隔离与恢复

- Redis键格式 `ticket-shop-admin:{environment}:session:{principalType}:{sid}`；其他键使用同一环境前缀，不把用户名、密码或JWT放进键。逻辑DB与前缀不是安全边界；生产需ACL/网络隔离。禁止FLUSHALL/FLUSHDB，清理只扫描本项目前缀并分批删除。
- Redis是可重建状态，不能用Redis快照覆盖MySQL；普通重启/缓存驱逐按MySQL重建未撤销会话，不更新认证时刻或绝对期限。
- 恢复旧MySQL备份前先从外围阻断认证，恢复后撤销所有旧客户/管理员会话并清理本项目Redis会话/预认证状态，核对账号变更后再解除阻断。签名密钥轮换不能替代账号状态核对。

## 备选方案与取舍

| 方案 | 判断 |
|---|---|
| JWT只验签，等exp到期 | 无法满足即时服务端撤销，否决 |
| JWT＋Redis作为唯一会话事实 | 缓存丢失/旧快照恢复需要额外持久化与一致性设计，不作为本期方案 |
| JWT＋Redis＋MySQL每次权威检查 | 已采纳；增加数据库读，但明确故障与撤销边界，适合当前未定规模 |

## 影响、迁移与回滚

当前未初始化业务代码与数据库，只更新文档，无数据迁移。后端增加Mapper、JWT和Redis适配及故障测试；前端Cookie和CSRF协议保持明确。后续已发布后再变更凭据格式，需要协议版本与重新登录策略，不能静默放宽验证。依赖版本更新先运行契约、撤销并发和恢复回归。

## 验证方式

JWT篡改/错audience/错用途/过期均拒绝；Redis停止或超时返回503；键丢失只能从有效MySQL记录重建；缓存中人为放回旧有效项，已撤销会话仍拒绝；冻结提交后新校验不能放行；MySQL提交成功但Redis写失败仍可回读真实状态；Redis与应用重启不续期；备份恢复后旧JWT不能复活。所有用例必须有HTTP结果、脱敏数据库和缓存回读，不能只看页面提示。

官方参考：[MyBatis-Plus安装](https://baomidou.com/getting-started/install/)、[Spring Security JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)、[MySQL CHECK约束](https://dev.mysql.com/doc/refman/8.0/en/create-table-check-constraints.html)。这些资料支持组件使用方式，跨存储一致性规则是本项目决策，尚未实现验证。
