# P2 数据模型与接口契约验收记录

- 日期：2026-11-03；环境：本机Mac＋经授权共享MySQL/Redis＋独立Testcontainers。
- 结论：P2本机验收通过，停在P3之前；远端CI尚未运行。
- 范围：迁移、数据模型/Mapper、协议与前端基础工具。没有真实登录、签名密钥、管理员开户或业务控制器。

## 后端交付与证据

| 步骤 | 交付与实际检查 |
|---|---|
| B2-01 | 11张表，显式类型、中文表/字段COMMENT、唯一键、具名索引、RESTRICT外键、UTC DATETIME(6)、版本字段及CHECK；独立MySQL查询metadata确认 |
| B2-02 | V1账号/控制、V2会话/策略、V3审计/限流/幂等；空库全量、先V1后升级、重复执行0迁移、修改测试副本校验和后阻断；clean拒绝 |
| B2-03 | 按业务包建立11组Entity/Mapper；审计/安全事件仅insert；CustomerView显式白名单；ID与版本字符串；UTC微秒TypeHandler；分页/排序枚举 |
| B2-04 | 自定义版本条件更新、具名锁方法、状态/期限更新、只撤销未撤销会话、只截短NORMAL会话；并发相同版本仅一个写入成功；审计失败同事务回滚 |
| B2-05 | OpenAPI3.0.3覆盖36路径/40操作；输入/输出/错误码、Cookie/Bearer/CSRF、分页、UTC、版本、幂等及一次性秘密均定义；本地引用、5个合成示例验证通过 |
| B2-06 | JWT声明、身份域、issuer/audience、Redis键/摘要/TTL、错误与剩余超时预算冻结在会话协议；未签发JWT、未生成密钥，P3实现 |

Maven verify：4项单元测试＋10项真实容器集成测试，共14项，失败0、错误0、跳过0。检查包含：规范账号唯一、实际CHECK拒绝码3819、外键拒绝删除、限流/幂等唯一性、UTC微秒、缺省字段保留/null清空、MP及自定义版本冲突、期限包含下界/排他上界、字面关键词、策略不延长、撤销不复活、审计回滚及依赖故障。

MySQL CHECK当前驱动表现为SQLState HY000/3819，未假设其一定映射DataIntegrityViolationException；测试核对真实错误码。HTTP业务错误处理在P3实现，不暴露SQL文本。

## 专用开发库实跑

显式辅助入口已执行Flyway至V3，再次执行无重复建表或初始化。只读回读：历史版本1/2/3全部success=1；customer_account、admin_account、customer_session、audit_event均0行；默认策略client_max_hours=24、version=0。

应用账号SHOW GRANTS仅USAGE＋本库SELECT/INSERT/UPDATE/DELETE；迁移账号额外CREATE/REFERENCES/INDEX/ALTER，仅限本库，没有全局管理或DROP。应用以DML账号启动后已成功校验3条迁移，liveness/readiness为200 UP，业务/api/v1/admin/users为401；前端代理实际返回UP。

仅迁移本项目专用开发库，不清理共享数据、不修改其他服务、不读关联.env。不修改ticket-shop或客户桌面数据。运行/迁移密码只经环境传给各自进程，不打印、写文件或进入前端；没有默认可用账号。

## 前端交付与证据

| 步骤 | 交付与实际检查 |
|---|---|
| F2-01 | 固定openapi-typescript7.10.1生成只读类型及枚举；api:check防漂移，Java错误码映射交叉核对；大ID、版本、分页、微秒UTC与时区工具 |
| F2-02 | 同源Cookie、内存CSRF、请求标识、UUID幂等头；401/403/409/429/503分类，不自动重试；非法JSON、缺字段、状态与错误码不匹配均不当成功；阻止路径穿越与跨源请求 |
| F2-03 | 管理员摘要/permissions只内存；me恢复方法及默认拒绝路由策略已定义，受限改密身份与正常身份隔离；P3再接真实登录和守卫 |
| F2-04 | 一次性秘密关闭清除且不会JSON序列化、保留输入的冲突状态、提交确认/空列表/日期选择契约；夏令时按下一本地零点计算 |

前端tsc＋vue-tsc、只读lint、生产build、API漂移检查均通过。Vitest18项通过；Chrome Playwright3项通过，现有环境页、禁用登录、404/网络反馈及主题刷新未回归。P2没有伪造认证响应或增加已登录后台页面。

## 构建与运行问题处理

本轮重打包覆盖了最初从target启动的P1 JAR，旧实例退出时发生类加载错误。已将开发辅助启动改为复制至runtime/backend独立JAR快照：实测目标JAR再次构建后，运行快照仍UP，随后正常完成Tomcat/Hikari优雅退出；最新构建已重新由快照启动。没有客户业务事务或数据受影响。

生成运行时枚举时遇到生成器对内联数组枚举的类型错误，已把DenialReason提取为命名schema并重新生成，未手工修改生成文件，也未通过跳过类型检查绕过。

原始证据位于忽略的runtime/p2-*.log、p2-database-evidence.txt、backend/target/*-reports及frontend/test-results。验收命令和权限流程见[本机手册](../runbooks/local-development.md)。CI已增加契约和类型漂移检查，远端运行结果仍待推送后核实。

## 停止点

P3尚未开始。离线超管引导、密码编码与验证、JWT签发/验签、Redis/MySQL认证链、CSRF服务端校验、真实登录/改密/退出、路由守卫接线均属于P3。数据库结构和协议测试不能替代这些功能的验收。

正式品牌素材、生产服务器/配置、容量、备份恢复演练、桌面接入与双平台验证仍在后续阶段，不阻断本机P2交付。
