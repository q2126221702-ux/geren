# 题域引擎 Android 安全检查

检查日期：2026-10-01（Asia/Shanghai）

修复状态（2026-10-02）：本报告所列问题及加固项已完成本地修复和验证，详见 [修复验收报告](D:/ziyong/release/security-remediation-2026-10-02.md)。下文保留原审查时的代码位置与结论；旧 Key 最终采取停用并重新填写的方式，避免猜测其服务商归属。

## 范围与结论

检查对象为 Android 应用 `com.xuzheng.tiyuengine`，版本名 `1.0.7`、版本码 `9`，包括 AI 请求与凭据、题库同步、学习备份、应用更新、组件权限、发布产物和运行时依赖。

发现 **3 项应修复的问题：1 项高优先级凭据泄露问题，2 项中优先级备份输入问题**。另有网络资源配额、请求取消和发布链路的加固建议。优先级为本次修复排序，不是 CVSS 评分。

本次审查没有修改应用源码、发布新版本或读取真实 API Key、签名私钥及口令。涉及密钥发送的结论来自完整调用链；没有向服务商发送测试凭据。服务端代理实现、用户真实手机及其账户状态不在本次检查范围内。

## 基线和验证

- 开发目录：`D:/ziyong`。
- 发布仓库：`D:/ziyong/.geren-sync`，Android 位于 `android/`；检查时 HEAD 为 `60df67330c36775b7d6162d019e6b8e02912ca3f`。
- 对比两目录的 93 个应用源码、资源、测试和配置文件；出现的字节差异为换行符差异，50 个 Kotlin/XML 文件归一化换行后内容一致。
- 本轮执行 `:app:testDebugUnitTest :app:lintRelease` 成功：24 项单元测试通过；Release Lint 为 0 error、49 warning、4 hint。现有测试未覆盖下述关键安全场景。
- 直接检查 `release/tiyuengine-1.0.7-patch1.apk`：包名和版本码正确，未开启 `debuggable` / `testOnly`，APK v2 签名验证通过。未用旧的构建缓存代替正式包验证。
- 对实际 Release 依赖树中的 59 个依赖坐标及 27 个对应多平台坐标，查询 [OSV querybatch API](https://google.github.io/osv.dev/api/#tag/api/operation/OSV_QueryAffectedBatch)，没有命中已收录漏洞。这不是全面无漏洞证明；不覆盖测试依赖、全部构建插件传递依赖或尚未公开的问题。
- 实际使用 OkHttp 4.12.0 和 Okio JVM 3.6.0；已核对 [CVE-2023-3635](https://github.com/advisories/GHSA-w33c-445m-f8w7)，其 Okio 修复版本为 3.4.0，当前版本不受该条目影响。

## SEC-01：切换服务商复用旧 Key，向另一服务商泄露凭据

**优先级：P1，高。** 影响使用自带 Key 且切换服务商的用户；共享模式不触发此路径。

正常操作即可触发：保存 DeepSeek Key → 选择 ChatAnywhere → Key 留空 → 点击“测试连接”。新服务商会收到原服务商的 Key，甚至不需要先保存新设置。

代码路径：

1. [AiSettingsScreen.kt:272](D:/ziyong/app/src/main/java/com/xuzheng/tiyuengine/ui/AiSettingsScreen.kt:272) 切换时仅更新 `providerId` 和 `model`；第 88 行仍把已有全局 Key 视为可用，第 90、134 行将新服务商和空草稿传给测试。
2. [AiClient.kt:27](D:/ziyong/app/src/main/java/com/xuzheng/tiyuengine/data/AiClient.kt:27) 对空草稿回退到 `settingsStore.apiKey()`；第 80、89 行选取新服务商 URL，第 92–93 行把原 Key 放入 Authorization。
3. [AiSettings.kt:67](D:/ziyong/app/src/main/java/com/xuzheng/tiyuengine/data/AiSettings.kt:67) 保存新服务商时，空 Key 也不会替换旧密钥；保存后从答题结果发起 AI 请求仍会出现相同问题。

**建议修复：** 将 Key 与 `providerId` 绑定；切换服务商时同时重置未保存的 Key 草稿，并只允许读取属于当前服务商的已存凭据。测试连接、保存和正式请求都应在数据层验证绑定关系。迁移现有全局 Key 时，将它绑定到原保存服务商，不能自动用于其他服务商。

**验收：** 用虚构 Key 和拦截请求的测试验证 A→B 的测试、保存和正式请求，确保 B 不会收到 A 的 Key；共享模式仍不产生 Authorization。

## SEC-02：未校验备份记录结构，损坏备份可覆盖正常数据

**优先级：P2，中。** 需要用户选择损坏或他人提供的文件并确认恢复；不是无交互远程攻击。

[LearningBackup.kt:52](D:/ziyong/app/src/main/java/com/xuzheng/tiyuengine/data/LearningBackup.kt:52) 只校验格式标识、版本、日期及顶层学习/错题数组数量，没有校验数组条目的必填字段、类型、枚举、范围和唯一性。以下样例能通过当前校验，并被预览为 1 条学习记录和 1 条错题：

```json
{"format":"tiyuengine-learning-backup","schemaVersion":1,"exportedAt":1,"learningRecords":[{}],"wrongItems":[{}],"favoriteIds":[]}
```

确认后，[LearningBackup.kt:46](D:/ziyong/app/src/main/java/com/xuzheng/tiyuengine/data/LearningBackup.kt:46) 覆盖原记录；[LearningStore.kt:165](D:/ziyong/app/src/main/java/com/xuzheng/tiyuengine/data/LearningStore.kt:165) 和 [WrongBookStore.kt:27](D:/ziyong/app/src/main/java/com/xuzheng/tiyuengine/data/WrongBookStore.kt:27) 因缺少字段解析失败，最终返回空列表。恢复流程仍可显示成功。该结论基于代码完整调用路径，没有在真实用户数据上执行恢复。

**建议修复：** 写入前将整份备份解析为应用实际使用的数据类型，校验所有必填字段、枚举、数值范围、字符串及嵌套数组上限、ID 唯一性和收藏结构。任意记录无效就拒绝整份备份，并指出错误位置。兼容旧版本缺少可选字段的正常备份。

**相关可靠性问题：** 第 46–48 行按顺序提交三份 SharedPreferences，忽略 `commit()` 返回值；存储失败时可能部分恢复。应增加写入结果检查及回滚/事务策略。重复错题 ID 还可能进入 Compose 列表的唯一 key，此附加崩溃风险未做真机验证。

**验收：** 缺字段、错误枚举、重复 ID、非法收藏及中途写入失败时，现有学习、错题和收藏数据均保持完整；正常和旧版备份往返恢复成功。

## SEC-03：备份大小限制在完整读取之后，无法阻止内存耗尽

**优先级：P2，中。** 用户只需选择文件，还未出现覆盖确认就会进入读取流程。

[BackupSettingsScreen.kt:81](D:/ziyong/app/src/main/java/com/xuzheng/tiyuengine/ui/BackupSettingsScreen.kt:81) 对外部内容 URI 使用无限制 `readText()`。直到整份文件读取完成，[LearningBackup.kt:53](D:/ziyong/app/src/main/java/com/xuzheng/tiyuengine/data/LearningBackup.kt:53) 才检查 5 MiB，而且 `toByteArray()` 会再分配一份内容。误选大文件、恶意文件或持续供给数据的文档提供者，可能先耗尽内存。

**隔离验证：** 使用项目同版 Kotlin stdlib 2.0.21 的 `TextStreamsKt.readText`、合成 64 MiB Reader 和独立 JVM `-Xmx32m`，在读取 8,396,800 个字符后得到 `OutOfMemoryError before validator=true`。这证明了读取顺序的问题；没有将其冒充为真实手机崩溃实测，也没有读取真实备份。

**建议修复：** 对原始输入流计数，最多读取 `MAX_BACKUP_BYTES + 1` 字节，超限立即关闭并提示；不能只依赖提供者声称的文件大小。避免为了长度检查再复制整个字符串。

**验收：** 普通超大文件和未知长度的流都能在达到上限时终止，不进入预览，不改变用户数据。

## 其他加固与可靠性建议

### 网络下载、ZIP 解压和 AI 响应配额

- [QuizRepository.kt:145](D:/ziyong/app/src/main/java/com/xuzheng/tiyuengine/data/QuizRepository.kt:145) 没有限制 ZIP 下载、解压总量、单文件大小或条目数量；第 123–126 行还会整体读取 JSON。当前来源是固定 HTTPS GitHub 仓库，触发需要仓库误加异常内容、仓库写入权限被滥用等条件，不能描述成任意网站直接利用。
- [AppUpdater.kt:118](D:/ziyong/app/src/main/java/com/xuzheng/tiyuengine/data/AppUpdater.kt:118) 下载 APK 没有实际字节上限，签名验证在完整落盘之后。异常大资产可能先消耗存储空间。
- [AiClient.kt:97](D:/ziyong/app/src/main/java/com/xuzheng/tiyuengine/data/AiClient.kt:97) 的错误/JSON 响应和第 104–119 行的流式内容缺少客户端大小上限；服务端 `max_tokens` 参数不能约束异常响应。

建议分别设置合理的实际字节/条目/题目数量限制、请求总时限，并在失败时清理临时文件。ZIP 配额需考虑跳过条目时 `closeEntry()` 仍可能继续解压数据。

### 请求取消

[AiClient.kt:96](D:/ziyong/app/src/main/java/com/xuzheng/tiyuengine/data/AiClient.kt:96) 使用同步 `execute()`，未把协程取消关联到 `Call.cancel()`。离开页面后，慢请求可能继续占用连接并消耗额度；120 秒 read timeout 不是整个请求的总期限。应建立取消桥接、设置 `callTimeout`，并重新抛出 `CancellationException`。这是请求生命周期问题，未证明存在凭据泄露或任意代码执行。[Kotlin 取消说明](https://github.com/Kotlin/kotlinx.coroutines/blob/master/docs/topics/coroutines-cancellation.md)、[OkHttp 4.12.0 Call 接口](https://raw.githubusercontent.com/square/okhttp/parent-4.12.0/okhttp/src/main/kotlin/okhttp3/Call.kt)。

### 凭据错误提示及 APK 身份

- [AiClient.kt:126](D:/ziyong/app/src/main/java/com/xuzheng/tiyuengine/data/AiClient.kt:126) 直接显示服务端错误文字的前 120 字。建议脱敏或使用固定本地提示，防止服务端回显凭据；当前未观察到实际回显。
- [AppUpdater.kt:138](D:/ziyong/app/src/main/java/com/xuzheng/tiyuengine/data/AppUpdater.kt:138) 已检查证书，但可进一步检查实际 APK 的包名和版本码是否与更新声明一致。这属于防止资产误发的附加保护，不是已确认的签名绕过漏洞。

### 明文流量和构建供应链

- 所有固定请求 URL 均为 HTTPS，也未发现关闭证书/主机名校验的代码。建议显式配置全局禁止 HTTP；不能仅靠 targetSdk 36 推断所有受支持系统的默认行为。Android 7.0 的缺省 `usesCleartextTraffic` 为 true，参见 [对应系统源码](https://android.googlesource.com/platform/frameworks/base/+/android-7.0.0_r1/core/java/android/content/pm/PackageParser.java) 和 [Network Security Configuration](https://developer.android.com/privacy-and-security/security-config)。当前未确认可利用的明文路径。
- Gradle Wrapper 可补 `distributionSha256Sum`；GitHub Actions 可固定完整提交 SHA，并为 Quality 工作流明确最小权限。未发现 `pull_request_target` 执行不可信代码的路径。[GitHub 官方加固建议](https://docs.github.com/en/actions/reference/security/secure-use)。

## 已确认有效的保护

- 共享模式的连接测试、单题解析和学情分析均不读取/发送个人 Key，Authorization 的构建也有模式检查。
- Key 通过 Android Keystore 的 AES-GCM 加密后存入私有 SharedPreferences；清除操作删除密文及 Keystore alias；未发现请求日志输出 Key。
- 学习导出不包含 AI 配置；Android 云备份和设备迁移均排除 `ai_settings.xml`、`ai_secrets.xml`，已核对正式 APK 内的规则。
- FileProvider 非导出，仅分享 `cache/updates/`，安装流程只授予读权限；符合 [Android 对窄路径和最小授权的建议](https://developer.android.com/privacy-and-security/risks/file-providers)。
- 普通第三方应用没有可调用的敏感导出入口；MainActivity 仅用于启动，APK 中 ProfileInstallReceiver 受 DUMP 权限保护。
- 下载 APK 会核对当前应用的签名证书，正式包未开启调试。
- 当前 ZIP 提取拒绝文件名中的 `/`；未确认经典 Zip Slip，未将 Windows 反斜杠语义错误套用到 Android。
- AI 输出以 Compose 文本显示，未发现执行输出、WebView JavaScript 桥接或动态执行远程代码的路径。
- Git 跟踪文件名检查未发现 JKS、keystore 或 `keystore.properties` 被纳入开发仓库及发布仓库。

## 建议修复顺序

1. 修复服务商和 Key 的绑定，并用虚构凭据覆盖所有发送入口。
2. 把备份字节限制前移到读取阶段，再补完整结构校验和写入失败保护。
3. 增加下载、解压、AI 响应的资源上限及请求取消。
4. 完成配置和构建供应链加固，最后用真实手机验收升级、备份和 Key 切换。

现有单测与 Lint 通过不能替代以上安全场景验收。本报告反映所审查版本和本次证据，不构成安全认证。
