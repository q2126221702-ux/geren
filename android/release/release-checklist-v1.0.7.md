# 题域引擎 1.0.7 发布验收

本清单记录已完成的发布验证；真实手机验收仍待完成。

- [x] Release APK 标识为 `com.xuzheng.tiyuengine`、`versionName 1.0.7`、`versionCode 8`
- [x] 候选 APK 签名证书 SHA-256：`909E933115503D0F0BEEECE3010B55839BE980244B6238321CD1F5E69584B28A`
- [x] JVM 单元测试：20 项通过
- [x] Detekt、Lint、Release APK/AAB 构建与 GitHub Actions 质量检查通过
- [x] 公共 Release 为非草稿、非预发布的 `v1.0.7`，且为最新版本
- [x] 公开 APK SHA-256 与本地正式包一致；源码提交、包哈希及发布页面已记录在 `legal/evidence-manifest-v1.0.7.md`
- [ ] 真实 Android 手机全新安装、启动和离线题库答题
- [ ] 使用同签名 1.0.6 正式 APK 覆盖升级，核对学习记录、错题和收藏保留
- [ ] 小屏幕、放大字体下检查首页操作按钮和答题选项不被底部操作栏遮挡
- [ ] 检查答题卡、结果页、复习、收藏、学习报告与设置的导航和返回键
- [ ] 检查深色模式对比度、页面切换、旋转、休眠恢复和网络切换
- [ ] 在目标应用商店完成内容分级、数据安全和联系方式表单

验收结果、设备型号、Android 版本和测试日期记录在 `acceptance-report-v1.0.7.md`。以上未勾选项目不得视为已通过。
