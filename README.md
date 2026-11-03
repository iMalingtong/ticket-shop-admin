# 票小理管理平台（ticket-shop-admin）

票小理的独立账号与授权管理项目，与桌面客户端 `ticket-shop` 平级维护。

## 项目职责

- Java 账号管理服务：账号密码、使用期限、账号状态、会话校验与撤销。
- 管理后台：用户管理、冻结/停用、延期、密码重置、会话及审计记录。
- 向票小理 macOS / Windows 客户端提供联网身份验证与使用资格校验。

票据、附件和报销数据继续保存在桌面端，本项目不接收或处理票面数据。

## 当前状态

已完成 P0～P3：文档、独立工程、数据/接口契约、真实身份认证与后台登录闭环。管理端使用 HttpOnly Cookie＋CSRF，客户端使用 JWT；共享开发库没有默认账号，正式开发管理员需通过终端离线引导设置。P4 客户管理尚未开始。

- [P3 验收记录](docs/verification/P3-authentication.md)（真实登录、会话、故障与浏览器验证）
- [P2 验收记录](docs/verification/P2-data-and-contract.md)（迁移、契约和本机验证结果）
- [OpenAPI 接口契约](docs/api/openapi.yaml)
- [Mapper 规范](docs/persistence/mapper-conventions.md)与[会话协议](docs/api/session-protocol.md)
- [P1 验收记录](docs/verification/P1-foundation.md)（实际版本、测试结果及阶段边界）
- [本机启动与检查](docs/runbooks/local-development.md)
- [产品需求文档](docs/PRD.md)
- [账号授权与管理后台开发需求说明书](docs/DEVELOPMENT_SPEC.md)（架构、环境、目录、数据库、接口、权限、实施阶段及验收）
- [分阶段开发步骤流程](docs/DEVELOPMENT_PLAN.md)（前后端分离、逐步任务与验收门禁）
- [技术决策记录](docs/decisions/ADR-0001-auth-and-persistence.md)（MyBatis-Plus、MySQL 8.0、JWT与Redis）
- [桌面客户端项目](../ticket-shop/README.md)（本地同级目录链接）

## 技术方向

- 后端：Java 21、Spring Boot 3.5 系列、Spring Security、MyBatis-Plus、Flyway。
- 认证与存储：JWT＋Redis 7，MySQL 8.0持久化账号、会话、撤销与审计；复用本机Docker服务。
- 后台前端：Vue Pure Admin 官方精简版、Vue 3、TypeScript、Element Plus。
- 工程方式：同仓库 `backend/`、`frontend/`，独立启动、构建和发布。
- 开发方式：先在 Mac 本机验收；暂时没有生产服务器，生产部署列为后续阶段。

后端精确版本固定在 Maven 配置与 Wrapper，前端固定在 pnpm-lock.yaml；上游来源见 frontend/UPSTREAM.md。两端独立 CI 已配置，远端尚未运行。

## 与桌面项目的边界

本项目单独管理源码、依赖、数据库、构建和发布，不加入桌面项目的 Maven 模块。桌面登录界面、凭据管理及本地业务门禁由 `ticket-shop` 实现，通过接口与本项目协作。

本机入口：http://127.0.0.1:8848/welcome；后端仅监听127.0.0.1:18080。启动方式见操作手册。P1不建业务表、不提供真实登录，Flyway将在P2提供迁移后启用。
