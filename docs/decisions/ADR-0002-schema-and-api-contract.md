# ADR-0002：P2 数据迁移、并发 Mapper 与 API 契约

- 状态：已采纳
- 日期：2026-11-03
- 关联：[开发说明书](../DEVELOPMENT_SPEC.md)、[阶段流程](../DEVELOPMENT_PLAN.md)、[OpenAPI](../api/openapi.yaml)

## 背景与约束

P1只有可连接的工程骨架。P2必须让MySQL结构、Java持久化模型、浏览器协议及后续桌面接入共享一个可检查的契约；本阶段不开放认证和业务控制器。

## 决策

1. V1建立管理员、客户、平台控制；V2建立策略、客户/管理会话及预认证；V3建立审计、安全事件、限流、幂等。只有控制和策略单例初始化，不提供默认管理员或密码。ID仍为BIGINT UNSIGNED，但本产品生成与API接受范围限定为正Java Long，JSON始终字符串；版本也用字符串，避免JS精度损失。
2. HTTP服务使用DML账号，启动只检查迁移校验和、缺失迁移及当前V3，不执行DDL。显式`java -jar ... --migrate`使用独立迁移凭据，并限制目标数据库命名空间；禁用clean、自动baseline及乱序。开发辅助脚本显式先迁移、再用DML账号启动。没有静默跳过结构检查的运行模式。
3. Flyway迁移不可改写。MySQL DDL并非整体事务：本阶段不提供自动回滚、clean或repair；失败需按发布手册检查部分结果，通过后续迁移或隔离恢复处理。校验失败必须阻止服务启动。
4. DATETIME(6)按UTC日历字段绑定/读取，使用自定义Instant TypeHandler，避免JVM默认时区干扰。允许1000–9999年，最多微秒精度；资格有效区间`[validFrom,validUntil)`。
5. 普通CRUD可用MyBatis-Plus；状态、版本、锁、撤销和策略截短使用具名Mapper。自定义更新显式`WHERE version = expectedVersion`及`version=version+1`，不能假设乐观锁插件自动保护任意XML。服务层负责把影响行数0映射409，并在权限检查后提供当前版本。插件分页最后执行，限制100条，排序使用枚举/固定SQL分支，禁止`${}`。
6. 锁顺序为平台控制（管理员/恢复相关）→策略单例→账号（多账号按id升序）→会话（按id升序）；不需要的锁可省略，已持有后不得倒序补锁。成功业务与审计、幂等提交在同一MySQL事务。P2仅提供和测试基础设施，完整业务事务在P3～P5编排。
7. OpenAPI3.0.3覆盖开发说明书的全部40个操作。登录正常/首次改密结果以nextAction区分；桌面正常登录将资格放入qualification，校验/账户接口直接返回资格快照。管理员登录只返回摘要、CSRF及HttpOnly Cookie，不向脚本返回JWT。
8. 页面刷新由后续P3调用me重取身份，再取csrf轮换内存CSRF。csrf端点在无有效管理会话时创建预认证绑定，已有正常/受限会话时轮换该绑定的CSRF。GET按Origin（若有）及同源Fetch Metadata/Referer校验来源，不要求脚本设置浏览器禁止设置的Origin头；所有管理POST/PATCH/PUT/DELETE仍要求允许Origin＋CSRF。
9. 含密码写请求不缓存秘密。首次结果可展示一次；相同幂等键重放仅返回committed/resourceId/secretAvailable=false。幂等表增加处理状态和可重放结果白名单JSON，严禁JWT、密码、摘要或CSRF进入结果快照。
10. TypeScript类型及运行时枚举由固定openapi-typescript7.10.1生成；生成文件不手改、不进行格式化lint，以`api:check`防漂移并全量类型检查。Python校验器只接受本地引用，并核对说明书路由、Java错误码HTTP映射及合成示例。当前页面不调用尚未实现的业务接口。

## 备选方案与取舍

- 运行账号持DDL并随HTTP启动自动迁移：操作较少，但扩大长期权限和误迁移风险，未采用。
- Java、TypeScript手工各写一套接口：初期方便但易漂移；采用OpenAPI生成前端类型和跨文件检查。
- 单凭MP乐观锁插件或拼接排序SQL：无法证明自定义领域更新及用户排序安全，采用明确SQL与枚举。

## 影响、迁移与回滚

P1空开发库前向迁移至V3；运行账号权限不扩大。独立迁移账号只在本项目库有SELECT/INSERT/UPDATE/DELETE/CREATE/ALTER/INDEX/REFERENCES，不授全局管理或DROP权限。开发脚本每次生成进程内随机密码，不输出或写入文件，不适用于生产。旧P1“无业务表”只是历史验收结果。

正式上线前仍需生产迁移账号、安全注入、备份及失败恢复流程；暂无生产服务器，本次不作生产验收。接口尚未对外发布，后续契约变更必须同步机器契约、说明书及类型检查。

## 验证方式与参考

实际MySQL检查全量/分步升级、重复执行、改写校验和阻断、唯一性、CHECK、FK、并发版本、UTC与期限边界、审计失败回滚、撤销不复活；DML账号实跑校验。前端覆盖大ID、微秒、DST、错误分类、CSRF/幂等头和无自动重试。

组件行为参考：[MyBatis-Plus分页](https://baomidou.com/en/plugins/pagination/)、[乐观锁](https://baomidou.com/plugins/optimistic-locker/)、[Flyway校验](https://documentation.red-gate.com/flyway/reference/commands/validate)、[openapi-typescript CLI](https://github.com/openapi-ts/openapi-typescript/blob/main/docs/cli.md)。上述组件文档不代替本项目的数据库实测。
