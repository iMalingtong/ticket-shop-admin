# 票小理管理后台

基于官方Pure Admin中文精简版，来源和删减见[UPSTREAM.md](UPSTREAM.md)，保留[LICENSE](LICENSE)。

使用Node22.23.3与pnpm11.25.0。在本目录执行`corepack pnpm@11.25.0 install --frozen-lockfile`，随后`corepack pnpm@11.25.0 dev`。开发地址http://127.0.0.1:8848，API代理到127.0.0.1:18080。

P3已提供真实登录、首次改密、身份恢复、本人改密与退出；不包含演示账号、Mock登录或前端令牌保存。服务端独立构建启动，dist不打包进JAR。真实认证使用HttpOnly Cookie＋内存CSRF，无默认账号，初始化见[本机手册](../docs/runbooks/local-development.md)。

P2已提供OpenAPI生成类型、统一请求、内存身份与CSRF、默认拒绝路由策略及协议工具；真实登录和守卫已在P3接线，客户管理页面尚未开始。生成与漂移检查使用`corepack pnpm@11.25.0 api:generate`、`corepack pnpm@11.25.0 api:check`，详见[前端协议](../docs/api/frontend-protocol.md)。
