# 上游来源与删减记录

- 官方仓库：https://github.com/pure-admin/pure-admin-thin
- 分支：main（中文精简版），package版本6.2.0。
- 锁定提交：f0ff132561ab684bb78379239adf29f9a38ac7f1。
- 引入日期：2026-11-02；保留LICENSE及原作者声明。
- 保留：布局、侧栏、主题、Element Plus、Pinia、Router、公共样式与组件。
- 移除：Mock目录、生产fake-server插件、演示权限页、演示通知组件及数据、演示账号密码、JS持有/刷新令牌及SSO样例、运行时CDN和上游发布工作流。
- 上游所有.env文件均未导入或读取；本项目公开开发地址在vite.config.ts配置，服务端秘密不进入前端。
- P1仅开放环境检查与不可提交的登录占位页。真实管理认证及权限在P3接入。
- 上游logo目前为模板标识，尚未导入桌面项目品牌资产；正式品牌资源由后续明确提供，不访问ticket-shop复制。
- 图标依赖按当前使用的ri/ep集合拆分，避免下载全量@iconify/json；不改变现有图标体系。
