# 本机开发、迁移与检查

## 环境与权限

- Java：项目.java-version绑定jenv openjdk64-21.0.12；Node：.node-version绑定fnm22.23.3。
- Maven Wrapper3.9.16（下载SHA256固定）；前端统一`corepack pnpm@11.25.0`及pnpm-lock.yaml。
- 经授权复用指定compose的mysql、redis，3306/6379；专用开发库ticket_shop_admin。
- 运行账号ticket_shop_admin_app只SELECT/INSERT/UPDATE/DELETE；独立迁移账号ticket_shop_admin_migrator只在本项目库持额外CREATE/ALTER/INDEX/REFERENCES，不持全局管理或DROP权限。
- P2已迁移V1～V3，11张业务表＋Flyway历史表；只有控制、策略单例，没有默认管理员或客户。

## 构建与测试

从项目根目录：

```sh
./backend/mvnw -B -f backend/pom.xml verify
python3 -m pip install --target runtime/python -r scripts/contract-requirements.txt
python3 scripts/validate-api-contract.py
corepack pnpm@11.25.0 --dir frontend install --frozen-lockfile
corepack pnpm@11.25.0 --dir frontend api:check
corepack pnpm@11.25.0 --dir frontend typecheck
corepack pnpm@11.25.0 --dir frontend lint:check
corepack pnpm@11.25.0 --dir frontend test:unit
corepack pnpm@11.25.0 --dir frontend build
```

verify要求Docker可用，强制运行独立MySQL/Redis Testcontainers，不把缺Docker静默记为跳过。MySQL测试检查真实CHECK、唯一键、外键、版本冲突、UTC、审计回滚与迁移升级；只操作隔离测试库。Redis只写唯一项目前缀键，故障测试只停止测试专用容器。

OpenAPI校验核对说明书全部路由、Java错误码和示例，不解析外部引用。生成类型变更时运行`api:generate`并审阅，再执行只读api:check；生成文件不手改或格式化，仍纳入tsc/vue-tsc。验证脚本依赖仅本地工具，不是Java运行依赖；CI使用Python3.12独立安装。

## 本机显式迁移与启动

本次用户已授权读取的文件为`/Users/malingtong/Desktop/workspace/server/docker-compose.yml`，仅用于准备本项目库及账号。使用：

```sh
python3 scripts/start-local-backend.py --compose /Users/malingtong/Desktop/workspace/server/docker-compose.yml --migrate-only
python3 scripts/start-local-backend.py --compose /Users/malingtong/Desktop/workspace/server/docker-compose.yml --auth-config "$PWD/runtime/keys/development-auth.json"
corepack pnpm@11.25.0 --dir frontend dev
```

第一条只执行迁移，不轮换运行账号密码、不启动HTTP；第二条先显式迁移、再用DML账号及明确指定的开发签名配置启动。脚本每次为专用账号生成随机密码，仅存在于进程环境，不打印或写文件；不读.env/env_file或关联凭据文件。管理密码仅用于docker exec准备专用库/账号，不注入Java；迁移密码不注入HTTP长期进程。

运行账号密码每次启动轮换，仅供本机单实例。18080占用时拒绝启动且不改运行账号密码；先停止自己的旧实例并确认退出，再重启。不得用于生产或共享账号的多实例场景。

启动脚本将构建JAR复制到忽略的runtime/backend独立快照，再从快照运行，避免后续Maven打包覆盖运行中的target JAR。构建产物与快照不含凭据。停止时在原终端Ctrl+C；后台启动则先用`lsof -nP -iTCP:18080 -sTCP:LISTEN`确认本项目PID，再发TERM并等退出。不要覆盖仍在运行的JAR或在旧进程退出前复用同一个日志文件。

[本机后台](http://127.0.0.1:8848/welcome)提供环境检查；[登录页](http://127.0.0.1:8848/login)已接真实认证、首次改密、本人改密与退出。没有默认账号，需先在终端执行下面的离线引导。模板Logo仍为占位；没有读取ticket-shop素材。前端开发服务器仅127.0.0.1:8848，后端仅127.0.0.1:18080，端口冲突不自动漂移。/api/health/*代理到/actuator/health/*，其他/api/*保留路径。

## 开发签名与离线管理员引导

本轮经明确授权新建runtime/keys/development-auth.json，RSA3072位、独立32字节HMAC；目录700、文件600且被Git忽略。仅限开发，不读其他凭据文件，不用于生产，不把内容复制到聊天或日志。重新启动读取同一份明确指定的配置，因此原JWT不会因进程重启自动换密钥而失效，也不会延长原上界。

以下均从本项目根目录执行。已有开发配置不需要重新创建；新环境经明确授权后才执行`python3 scripts/create-local-auth-config.py --create`，已存在则拒绝覆盖、读取或自动轮换。

正式管理员由使用者在自己的交互终端输入账号、名称、初始密码及确认。先按上一节停止本项目HTTP实例，确认18080释放，再执行：

```sh
python3 scripts/start-local-backend.py --compose /Users/malingtong/Desktop/workspace/server/docker-compose.yml --auth-config "$PWD/runtime/keys/development-auth.json" --bootstrap-admin
python3 scripts/start-local-backend.py --compose /Users/malingtong/Desktop/workspace/server/docker-compose.yml --auth-config "$PWD/runtime/keys/development-auth.json"
```

第一条不启动HTTP，不接受密码参数或管道输入，密码隐藏；仅未初始化且没有管理员时成功。控制行锁、创建超管、审计、initialized标记同事务；失败不自动修复/重建，重复引导拒绝。第二条启动服务，然后访问/login，首次必须改密并重新登录。当前共享开发库没有预置管理员；所有验收账号都在销毁的隔离测试库内。

通用入口为`java -jar <独立快照.jar> --bootstrap-admin`，需由安全注入流程提供数据库及签名环境变量。签名仅test profile允许内存临时密钥，供独立测试使用；development/production缺配置拒绝启动，不静默换钥。后续正式密钥轮换须单独设计和验收。

## 不使用本机辅助脚本时

通过已批准的安全注入流程提供变量，禁止把密码写进命令行、仓库或日志：

| 用途 | 必需设置 |
|---|---|
| HTTP服务 | SPRING_PROFILES_ACTIVE（development/test/production三选一）、TICKET_SHOP_DB_USERNAME/PASSWORD |
| 开发地址 | development默认127.0.0.1:3306/ticket_shop_admin、Redis127.0.0.1:6379；可显式覆盖TICKET_SHOP_DB_URL、TICKET_SHOP_REDIS_HOST/PORT |
| 签名 | TICKET_SHOP_JWT_PRIVATE_KEY（PKCS8 DER Base64）、TICKET_SHOP_JWT_PUBLIC_KEY（X509 DER Base64）、TICKET_SHOP_HMAC_KEY（至少32字节Base64）、TICKET_SHOP_JWT_KEY_ID（1–64 ASCII） |
| 管理来源 | TICKET_SHOP_ALLOWED_ORIGINS，精确Origin逗号分隔；生产必须HTTPS，Cookie启用Secure；无通配符或远程取钥 |
| 客户版本 | 协议首版固定1；TICKET_SHOP_MINIMUM_CLIENT_VERSION默认空，用户已确认桌面接入时才确定并启用 |
| Redis认证 | TICKET_SHOP_REDIS_DATABASE默认0；USERNAME/PASSWORD按实际服务可选；项目前缀按环境隔离 |
| 生产 | 必须提供DB_URL、Redis地址及TICKET_SHOP_BIND_ADDRESS，不继承开发地址；本次不作生产部署 |
| 迁移进程 | SPRING_PROFILES_ACTIVE、TICKET_SHOP_DB_URL、TICKET_SHOP_MIGRATION_USERNAME/PASSWORD |

迁移入口为`java -jar backend/target/ticket-shop-admin-0.1.0-SNAPSHOT.jar --migrate`，仅接受本项目命名空间数据库和独立变量凭据。正常HTTP启动不执行DDL，但会校验MySQL8.0.16以上8.0补丁、迁移校验和及当前V3，缺失/错误版本启动失败。测试显式用隔离容器账号自动迁移，不是生产运行方式。

MySQL DDL不是整体事务。迁移失败不自动重试、rollback、repair、baseline或clean；先停在失败状态、检查部分DDL及备份，在隔离环境定位，审阅后提供补偿迁移或恢复方案。不要改写已执行迁移绕过校验。当前迁移不需要DROP，后续确有需要时另行审阅权限。

## 浏览器回归

```sh
corepack pnpm@11.25.0 --dir frontend exec playwright install chromium
corepack pnpm@11.25.0 --dir frontend test:e2e
```

本机已安装Chrome时可用`PLAYWRIGHT_CHANNEL=chrome corepack pnpm@11.25.0 --dir frontend test:e2e`，CI默认Playwright配套Chromium。常规E2E检查空登录表单、状态反馈、404、连接错误及主题刷新。真实认证E2E由Java隔离测试夹具启动独立8849前端、随机端口后端和独立数据库，不能拿合成密码尝试共享开发库。完整验收使用`P3_BROWSER=true PLAYWRIGHT_CHANNEL=chrome ./backend/mvnw -B -f backend/pom.xml verify`；CI不设置Chrome channel，使用配套Chromium。测试截图、报告、日志均忽略，不提交。

## 回读与阶段停止点

- liveness、readiness均只返回status；数据库和Redis就绪时readiness为200 UP，依赖不可用为503。
- 未认证/auth/me为结构化401；首次改密凭据访问me为403 PASSWORD_CHANGE_REQUIRED；P4管理接口仍关闭。前端身份/CSRF仅内存，JWT仅HttpOnly Cookie。
- 不执行docker compose down、删除卷、停止共享服务或FLUSHALL/FLUSHDB；只启动明确授权服务，故障验证放独立容器。
- P3完成后停止，P4客户管理等待确认。远端CI、生产部署、桌面接入分别验收。
