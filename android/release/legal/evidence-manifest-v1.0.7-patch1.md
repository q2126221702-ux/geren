# 题域引擎 1.0.7 补丁 1 证据清单

生成日期：2026 年 9 月 30 日

- 源码提交：`6564a2e4090feb342364e000bfe93ff9d29bf763`
- 发布标签：`v1.0.7.1`（补丁发布标识；应用内版本名保持 `1.0.7`）
- 应用包名：`com.xuzheng.tiyuengine`
- versionName：`1.0.7`；versionCode：`9`
- APK SHA-256：`AA8F3587F54E1FF1A0B46103F7EEB4FBBCE60769900371D76434077D79AE19B4`
- 签名证书 SHA-256：`909E933115503D0F0BEEECE3010B55839BE980244B6238321CD1F5E69584B28A`（与原 1.0.7 一致）
- GitHub Actions 质量检查：`https://github.com/q2126221702-ux/geren/actions/runs/36721899437`（通过）
- GitHub Release：`https://github.com/q2126221702-ux/geren/releases/tag/v1.0.7.1`（公开、非预发布、latest）
- 正式 APK：`https://github.com/q2126221702-ux/geren/releases/download/v1.0.7.1/tiyuengine-1.0.7-patch1.apk`

## 验证记录

- 本地 Detekt、Lint、24 项 JVM 单元测试和签名 Release APK 构建通过。
- APK 包名、版本名、内部版本码和签名证书已核对。
- GitHub Release 公开 APK 的 SHA-256 与本地正式包一致。
- 模拟器在系统网络状态未验证的情况下，首次点击成功发起默认 AI 分析；加载提示立即可见，滚动离开卡片后返回，结果与冷却状态均保留。
- 旧客户端按补丁标签发现更新；新客户端按版本名和内部版本码判断，安装后不会重复提示同一个补丁。
- 真实手机覆盖安装、学习数据保留及主要页面体验仍待用户验收。

原 `v1.0.7` Release 和证据清单保留，本次补丁不重写原发布标签。此清单不包含签名密钥及密码。
