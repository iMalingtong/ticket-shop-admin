# 前端协议与组件边界（P2）

- 唯一机器契约：[openapi.yaml](openapi.yaml)。生成类型/运行时枚举位于frontend/src/api/generated/schema.ts，`api:generate`更新，`api:check`只读检查漂移；错误码还与Java枚举交叉核对。
- ID与版本始终字符串，禁止Number(id)；分页1开始、大小10/20/50/100；时间只接受1000～9999年UTC带Z、最多6位小数。日期控件通过exclusiveDayEnd按选中时区求次日零点，跨夏令时不直接加24小时。重复零点取较早者，不存在零点则要求选择明确时刻。
- createAdminClient只发送本源/api/v1/admin路径；阻止路径穿越及编码路径。Cookie由浏览器管理，不生成Authorization头。写请求需内存CSRF；非auth写请求还必须由弹窗持有稳定UUID幂等键。没有自动重试、refresh token、密码或JWT持久化。
- 每个响应验证requestId/serverNow及data；端点decoder继续核验业务载荷，不能只检查HTTP200。错误按401认证、403权限/资格、409冲突、429限流、503/网络/格式错误不可用分流；状态码与错误码不匹配视为无效响应。错误对象不保留原始请求、密码或任意响应正文。
- user store只持内存管理员摘要、permissions和身份阶段。restoreIdentity定义为刷新时调用me重取，CSRF另取；当前P2不从应用入口触发。decideAccess对未声明权限的业务路由默认拒绝；受限改密身份只能进入改密页面，不能靠浏览器状态授予服务端权限。真实登录与守卫在P3接线。

## 统一交互状态

| 场景 | 组件契约 |
|---|---|
| 提交 | MutationDialogState进入SUBMITTING，保留input/version/idempotencyKey；阻止重复提交和关闭中断，不清空密码之外必要输入 |
| 并发冲突 | 进入CONFLICT，保留原输入及服务端currentVersion；刷新后人工确认，不自动重放覆盖 |
| 成功与缓存待同步 | COMMITTED明确显示数据库已提交；PENDING提示待同步，不能改成“操作失败” |
| 临时密码 | 仅secretAvailable=true时将密码放入弹窗私有OneTimeSecret；关闭/离开清除，不写Pinia持久化、日志、剪贴板自动复制或后续详情 |
| 幂等结果不含秘密 | secretAvailable=false显示已提交及资源标识，提供另发重置入口，不伪造原密码 |
| 冻结/停用/撤销 | 确认文案说明撤销现有会话，必要原因1～500字；缩短期限显示前后时刻并要求确认 |
| 空列表 | 只有成功响应且items=[]显示空状态；网络/权限/解析失败使用错误状态及人工重试，不假装无数据 |
| 权限错误 | 显示受限入口；不清空仍需保留的编辑上下文，不循环重登或循环弹窗 |

组件外观和实际业务页面在P3/P6完成；本阶段只落实类型、状态约定、协议适配及测试。管理操作由服务器执行，浏览器权限可见性不构成授权。
