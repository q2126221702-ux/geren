# 题域引擎安全修复验收

日期：2026-10-02（Asia/Shanghai）
应用：`com.xuzheng.tiyuengine`，版本名 `1.0.7`，版本码 `10`。
状态：本报告记录发布前完成的本地修复、验证和签名构建。正式发布状态及下载文件见 [v1.0.7.2 Release](https://github.com/q2126221702-ux/geren/releases/tag/v1.0.7.2)。

## 修复结果

| 审查项 | 已完成的修复 |
| --- | --- |
| SEC-01：跨服务商复用 Key | 凭据按 providerId 分开保存，AES-GCM AAD 绑定 providerId；测试、保存、正式请求均按当前服务商读取。切换服务商清空输入草稿，异步操作期间锁定相关设置。 |
| 旧 Key 的安全迁移 | 旧版全局密钥没有可信的服务商归属，因此停用旧密文并提示重新填写，绝不猜测归属或自动发送。共享模式继续不读取、不发送个人 Key。此方案替代原审查报告中的自动绑定建议。 |
| SEC-02：损坏备份覆盖数据 | 写入前验证全部必填字段、类型、枚举、范围、唯一性及嵌套数量；兼容旧版可选字段。恢复事务先写持久化日志，检查所有提交结果；失败回滚，中断后在下一次访问数据前恢复。 |
| SEC-03：读取完成后才检查大小 | 导入时按实际字节限制为 5 MiB，超限立即终止；不信任文件提供者声明的大小；解析前检查 JSON 深度。 |
| 下载和解压配额 | ZIP 下载 128 MiB、总解压 256 MiB、题库数据 16 MiB、单 JSON 2 MiB、条目 10,000、题库文件 512；跳过的 ZIP 条目也计入总解压量。限制题量和文本长度，拒绝重复引用同一文件及越界路径。 |
| 题库替换可靠性 | 校验完成后交换目录，保留旧目录以恢复中断；网络下载与读取/交换使用不同锁，避免读取等待整个下载。 |
| AI 网络请求 | 阻断重定向；限制 JSON、错误正文、SSE 行、累计流量与输出；解析前限制嵌套；协程取消会取消 HTTP 调用，并设置总超时。错误提示使用本地固定文字。正常 SSE 内容、usage 与结束事件保持兼容。 |
| 更新安装包 | 发布元数据限 1 MiB、APK 限 100 MiB；限定 HTTPS GitHub 下载来源和跳转；检查非空签名、包名、递增版本码及发布元数据，安装前再次验证；失败清理残留文件。 |
| 其他可靠性 | 错题次数饱和计数避免整数溢出；恢复日志写入前检查 32 MiB 上限；备份导出先完成序列化再打开目标文件。 |
| 系统和构建链路 | API 24 起显式禁止明文 HTTP；Gradle 8.13 分发包固定 SHA-256；GitHub Actions 固定完整提交 SHA，设置最小权限并关闭 checkout 凭据持久化。 |

## 验证

最终命令成功结束：

```text
gradlew.bat detekt :app:testDebugUnitTest :app:lintRelease :app:assembleRelease :app:bundleRelease :app:compileDebugAndroidTestKotlin --continue --console=plain
BUILD SUCCESSFUL
```

- JVM / Robolectric：60 项测试，0 失败、0 错误、0 跳过。
- 其中 AI 安全和网络 16 项、备份 12 项、下载安全 8 项、题库兼容/恢复/边界 4 项；其余为现有功能回归。
- Detekt：通过，没有扩大既有 baseline 来掩盖本次问题。
- Release Lint：0 error、44 warning、4 hint；仍有既有静态检查提示，不能表述为全项目零警告。
- 设备测试源码编译通过；没有连接手机或模拟器，因此没有声称完成设备安装、Keystore 真机或 UI 实测。
- 题库解析仍为 23 套、987 题；最终 APK 的 25 个 data 资源文件名与源目录全部一致。Windows Robolectric 无法读取打包资源的中文文件名，所以解析兼容性用隔离目录内的原始资源副本验证，APK 打包资源另行核对。
- APK v2 签名验证成功，证书与 patch1 一致；包名、版本码、版本名正确，未启用 debuggable / testOnly。
- AAB 构建签名成功，并通过 JarFile 逐项完整读取验证 124 个签名条目，证书相同。jarsigner 同时提示自签名证书、无时间戳和 ZIP 中 manifest 排列导致的 JarInputStream 警告；未做应用商店上传验收。
- 修改过的 27 个应用/测试文件已同步到本地发布仓库 `D:/ziyong/.geren-sync/android` 并逐文件校验一致；工作流和 wrapper 修复也在该仓库中。保留了工作区原有改动。

构建日志：`D:/ziyong/build/security-verification.log`。
单元测试报告：`D:/ziyong/app/build/reports/tests/testDebugUnitTest/index.html`。
Lint 报告：`D:/ziyong/app/build/reports/lint-results-release.html`。

## 产物

| 文件 | 字节 | SHA-256 |
| --- | ---: | --- |
| tiyuengine-1.0.7-patch2.apk | 4,919,748 | `1b0cea1a39819ded9fbe3300c1841dc8569a1b3913404f46ab31f2c564c1cf4d` |
| tiyuengine-1.0.7-patch2.aab | 7,078,023 | `9b41ab08d472ea4e436611e80fb21377b77d3895eeb4ed5e5732f870c1a24eec` |

签名证书 SHA-256：`909e933115503d0f0beeece3010b55839be980244b6238321cd1f5e69584b28a`。

升级提示：使用自带 Key 的用户需要在 AI 设置中重新填写所属服务商的 Key 一次。学习记录、错题、收藏及共享 AI 模式不需要迁移操作。

本轮完成此前审查列出的问题与加固项，并封堵复审发现的题库重复引用配额绕过。结论限于此次客户端代码和本地验证，不覆盖外部 AI 代理服务端、真实账户状态或未知漏洞。
