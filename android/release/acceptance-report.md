# 题域引擎 1.0.6 验收报告

验收日期：2026 年 7 月 12 日

## 构建身份

- applicationId：`com.xuzheng.tiyuengine`
- versionName：`1.0.6`
- versionCode：`7`
- minSdk：24
- targetSdk：36
- Release：R8 代码压缩、资源压缩、正式 JKS 签名

## 自动化结果

- JVM 单元测试：20 项通过
- Detekt + Lint：0 error
- Release APK：构建、签名校验通过
- Release AAB：构建、签名通过

## 设备行为结果

测试设备：Android Emulator `Medium_Phone_API_36.1`，API 36

- Debug 包安装与启动：通过
- 设置页重构与子页面导航：通过
- 答题退出确认与返回键层级：通过
- 期末卷合并入口与选卷：通过
- 答题卡 Bottom Sheet：通过
- 结果页默认错题筛选与 AI 折叠：通过
- 下拉刷新同步与离线横幅：通过
- 深色模式跟随系统：通过

## 权限与数据

- `android.permission.INTERNET` 用于题库同步、GitHub Releases 更新检查及 AI 请求
- `android.permission.ACCESS_NETWORK_STATE` 用于离线状态提示
- `android.permission.REQUEST_INSTALL_PACKAGES` 只在用户确认更新后调用系统安装器
- 无广告、分析、账号、定位、相机、相册、通讯录或麦克风权限
- 云备份和设备迁移仅包含 SharedPreferences 中的学习记录、错题进度及同步时间

## 尚需人工完成

- 连接一台真实 Android 手机，完成安装、答题、旋转/返回键、休眠恢复和网络切换测试
- 在应用商店填写内容分级、数据安全和联系方式
