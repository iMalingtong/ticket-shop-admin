# P1 工程骨架验收记录

- 日期：2026-11-02（本机Mac）
- 结论：P1本机验收通过；P2尚未开始。CI配置已建立，远端未运行。
- 范围：独立backend/frontend、构建与配置、环境检查、安全关闭业务入口；没有账号或授权业务实现。

## 环境与版本

| 项目 | 本次实测/锁定 |
|---|---|
| Java / Maven | OpenJDK21.0.12（jenv项目绑定）/ Wrapper3.9.16，下载SHA256校验 |
| Spring Boot / Security | 3.5.16 / 6.5.11 |
| MyBatis-Plus / MyBatis | 3.5.18（Boot3 starter＋分页解析模块）/ 3.5.19 |
| Flyway / Connector-J | 11.7.2（core＋mysql）/ 9.7.0 |
| Spring Data Redis / Testcontainers | 3.5.13 / 1.21.4 |
| Docker共享服务 | mysql:8.0 → MySQL8.0.46；redis:7-alpine → Redis7.4.10 |
| Node / pnpm | 22.23.3 / 11.25.0，显式Corepack调用 |
| 官方前端模板 | pure-admin/pure-admin-thin中文main，6.2.0，SHA f0ff132561ab684bb78379239adf29f9a38ac7f1，保留MIT LICENSE |
| Vue / Element Plus / Vite / TypeScript | 3.5.22 / 2.11.5 / 7.1.12 / 5.9.3 |
| Vitest / Playwright | 3.2.4 / 1.56.1；本机E2E使用已安装Chrome，CI默认配套Chromium |

本次本地镜像ID（用于追踪此次环境；tag未来可能变化）：

- mysql：sha256:7dcddc01f13bab2f15cde676d44d01f61fc9f99fe7785e86196dfc07d358ae2b
- redis：sha256:e7723ff73d963f5cc6d9c4643ea3d989527a402a319239054e9472a7fb9219a2

后端实际依赖树可用Maven dependency:tree重建；前端精确依赖以pnpm-lock.yaml为准。没有修改全局Java/Node默认值。

## 已执行检查与结果

| 检查 | 实际结果 |
|---|---|
| Maven verify | 成功生成独立可执行JAR；2项单元测试＋4项集成测试，失败0、跳过0 |
| 后端配置边界 | 无运行profile或多个运行profile均启动失败；显式环境的时钟为UTC |
| 独立MySQL测试容器 | CHECK拒绝非法值、会话UTC、无业务表 |
| 独立Redis测试容器 | 唯一项目前缀键写读、TTL、删除成功 |
| 依赖故障 | 仅停止测试专用Redis后readiness503，liveness200；未停止共享Redis |
| 访问控制 | 未认证业务GET及未开放登录POST为401；聚合健康入口401，仅两个探针公开且无详情 |
| 共享开发环境 | 创建ticket_shop_admin库及ticket_shop_admin_app账号；SHOW TABLES为空，应用仅有DML授权 |
| 本机后端实跑 | 18080存活与就绪均200且只返回status UP；业务接口401 |
| 前端安装 | 固定pnpm与lockfile安装成功；显式允许所需依赖构建脚本 |
| 类型、lint、构建 | tsc＋vue-tsc、只读eslint、Vite生产构建通过 |
| Vitest | 3项通过：UP有效性、503 DOWN分类、无效响应及网络失败 |
| Playwright | 3项通过：环境检查与禁用登录、404/连接错误、主题刷新保留且无pageerror |
| 浏览器实际页面 | Chrome检查浅色/深色布局、连接按钮显示基础服务已就绪，主题刷新保留 |
| 前后端代理 | 8848/api/health/readiness实际命中18080/actuator/health/readiness，返回UP |

原始本机输出保留在忽略的runtime日志、backend/target/*-reports、frontend/test-results中；截图含light.png与dark.png，均无凭据。它们是本机验证产物，不是正式源码。

## 授权操作与隔离

用户明确允许读取指定server/docker-compose.yml后，又明确允许仅启动其中mysql、redis并创建本项目专用库/账号。本次compose使用--env-file /dev/null，未加载关联.env；未启动其他服务，未删除临时容器或数据卷。

开发辅助脚本只读取显式指定的compose，管理密码仅用于数据库准备；应用使用专用随机密码，密码不打印、不写文件、不进入前端。每次辅助启动会轮换专用开发账号密码，仅支持本机单实例；详见[操作手册](../runbooks/local-development.md)。共享服务沿用原有端口映射，没有修改compose内容或既有网络配置。

没有修改或导入桌面ticket-shop的代码、配置、素材与数据。环境故障测试使用独立Testcontainers，不对共享数据库执行DDL测试、不清空Redis。

## 阶段边界与下一阶段

- Flyway依赖已引入但P1明确禁用；库中没有业务表和迁移历史表。P2提供迁移及独立迁移权限，再启用Flyway。
- 真实登录、JWT签发/撤销、CSRF业务协议、账号管理、权限与审计均尚未实现；环境UP不代表授权功能可用。
- 页面Logo沿用官方模板占位，未读取桌面品牌资产；后续获得明确品牌资源后替换。
- 未验证生产部署、容量、桌面接入与远端CI；没有生产服务器，不作上线验收结论。
- P1退出后停止，待用户确认进入P2：数据模型、Flyway迁移、Mapper规范和完整OpenAPI契约。
