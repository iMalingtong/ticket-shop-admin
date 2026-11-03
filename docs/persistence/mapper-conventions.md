# 持久化与 Mapper 约定

## 目录与职责

按业务包放在`com.ticketshop.admin.<module>.infrastructure`：Entity只用于数据库；Mapper负责参数化SQL。XML位于`resources/mapper/<module>`。控制器不得访问Mapper或返回Entity，应用服务编排权限、锁、事务、审计及幂等。

11张表及中文字段注释以V1～V3 SQL为准；不引入ORM自动建表。客户对外数据使用显式CustomerView白名单；公共成功/错误DTO位于common/api。ID和版本对外为字符串，统计及分页数量为安全整数；所有时间为UTC带Z字符串。

## 锁与版本

1. 引导、管理员数量保护、恢复操作先锁platform_control；策略相关操作先锁login_policy。再锁目标账号，最后锁会话；多对象按id升序，禁止倒序补锁。
2. 登录时密码计算可在事务外，但事务内必须复查密码版本、状态、期限再创建会话。冻结、改密、改期、撤销在相同账号锁约定下执行。
3. CustomerAccountMapper的lockById只可在事务内调用。updateProfile显式区分remark未传与null清空；updateStatus必须传原状态；updateValidity由服务先判是否缩短，SQL仍校验版本及期限CHECK。
4. 自定义XML条件更新均携带expectedVersion并递增version。ApiValues.requireChanged将0行视为版本冲突；HTTP异常处理与重读当前版本在后续服务层实现。禁用“保存整个表单实体覆盖数据库”。
5. MP的@Version覆盖支持的通用更新，不能当作自定义XML保证。状态改动必须由后续事务同时写入审计；P2的Mapper不是可单独开放的管理功能。

## 会话与策略

revokeUnrevoked只写未撤销行，重复执行不改变原撤销时间；没有“清空撤销”方法。shortenUnexpired只截短尚有效NORMAL会话，不恢复旧上界；账号锁、策略变更、审计及版本提升仍由后续应用事务处理。密码或状态变更不允许使用通用updateById绕过撤销流程。

JWT/CSRF/账号HMAC/请求指纹只保存摘要，byte[]对应BINARY(32)。sid/jti为43字符无填充Base64URL，ASCII二进制排序；客户和管理员各自独立唯一。时间TypeHandler直接绑定UTC LocalDateTime，保留6位微秒，不依赖本机Asia/Shanghai。

## 分页与过滤

page≥1，pageSize只允许10/20/50/100；进入Mapper前使用ApiValues校验。CustomerFilter.Sort是枚举，XML固定分支，无用户SQL字符串。按全结果筛选排序后分页，追加同向id稳定排序；最近登录null始终最后。keyword使用LOCATE字面子串，不把百分号、下划线或引号当作SQL。

validUntilFrom包含、validUntilTo不包含；期限状态以一次服务端now计算。只接受ASCII登录名，保留原大小写、规范键小写；不要直接用显示名或数据库默认排序规则代替规范化。

## 审计与幂等

AuditEventMapper、LoginSecurityEventMapper只提供insert，不提供修改/删除API。调用者在事务中分配ID，仅传白名单业务字段；审计失败则业务事务回滚。失败事件在主事务回滚后单独记录，不伪造成功。

mutation_request以actor_type/actor_id/idempotency_key唯一，route及fingerprint参与内容一致性检查；状态PROCESSING/COMMITTED、result_code/resource_id/result_snapshot记录可重放结果。不保存完整响应或秘密。秘密字段参与指纹时用服务端HMAC，密钥不入库；缓存待同步状态按MySQL已提交事实回复，不能误报未生效。

## 迁移与验证

Flyway CLI明确执行迁移；HTTP只校验已迁移V3。生产不得把迁移凭据注入HTTP长期进程。不要改写已执行SQL、自动baseline、repair或clean；MySQL DDL失败需人工核对部分结果。本次独立容器已验证CHECK的实际拒绝码3819；JDBC当前把该错误归为SQLState HY000，测试检查真实错误码，后续HTTP处理不能把SQL消息暴露给用户。
