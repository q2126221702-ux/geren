# 题域引擎 1.0.7 验收报告

发布日期：2026 年 9 月 30 日

## 正式包身份

- applicationId：`com.xuzheng.tiyuengine`
- versionName：`1.0.7`
- versionCode：`8`
- 正式 APK：`release/tiyuengine-1.0.7.apk`
- APK 签名证书 SHA-256：`909E933115503D0F0BEEECE3010B55839BE980244B6238321CD1F5E69584B28A`
- APK SHA-256：`BA87B8363D5DCD78DC383FCDC36537A174539CEB44CB3D9E4F36C170B889F5FD`
- AAB SHA-256：`800E01497ADBC4A7AFF2EA35E7F3018E510B5571D6B9B3F583589C8E07DF1DEF`
- 源码提交：`5a2cef9d69facd4f7946cad534b2df76925f9398`
- GitHub Release：`https://github.com/q2126221702-ux/geren/releases/tag/v1.0.7`

## 已验证

- JVM 单元测试：20 项通过。
- Detekt、Lint、Release APK/AAB 构建与 GitHub Actions 质量检查通过。
- 正式 APK 的包名、版本号、版本码及签名证书如上；与 1.0.6 的签名证书一致。
- GitHub `latest` 指向 `v1.0.7`；公开 APK 的 SHA-256 与本地包一致。

## 真实手机验收：待测

设备型号、Android 版本、测试日期：待记录。

- 全新安装、启动、离线题库读取及完整答题流程：待测。
- 从同签名 1.0.6 正式版覆盖安装，核对学习记录、错题和收藏：待测。
- 小屏幕与放大字体下首页按钮、答题选项及底部操作栏布局：待测。
- 答题卡、结果页、复习、收藏、学习报告、设置及返回键层级：待测。
- 深色模式对比度、页面切换、旋转、休眠恢复和网络切换：待测。

## 证据

哈希、源码提交、质量检查与发布页面汇总在 `legal/evidence-manifest-v1.0.7.md`。本报告不代表 1.0.7 已完成真实设备验收。
