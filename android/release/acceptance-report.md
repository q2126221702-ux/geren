# 题域引擎 1.0.4 验收报告

验收日期：2026 年 7 月 12 日

## 构建身份

- applicationId：`com.xuzheng.tiyuengine`
- versionName：`1.0.4`
- versionCode：`5`
- minSdk：24
- targetSdk：36
- Release：R8 代码压缩、资源压缩、正式 JKS 签名

## 自动化结果

- JVM 单元测试：通过
- Android 设备测试：2 项通过
- Release APK：构建、签名校验通过
- Release AAB：构建、签名通过

## 设备行为结果

测试设备：Android Emulator `sdk_gphone64_x86_64`，API 36

- 正式包全新安装：通过
- 应用启动及版本显示：通过
- 完全断网读取 23 套内置题库：通过
- 恢复网络后 GitHub 同步：通过，23 套、987 题
- 错题记录和连续三次答对归档：设备测试通过
- 启动器自适应图标：通过

## 权限与数据

- `android.permission.INTERNET` 用于题库同步和 GitHub Releases 更新检查
- `android.permission.REQUEST_INSTALL_PACKAGES` 只在用户确认更新后调用系统安装器
- 无广告、分析、账号、定位、相机、相册、通讯录或麦克风权限
- 云备份和设备迁移仅包含 SharedPreferences 中的学习记录、错题进度及同步时间
- 下载题库和缓存不进入系统备份

## 尚需人工完成

- 连接一台真实 Android 手机，完成安装、答题、旋转/返回键、休眠恢复和网络切换测试
- 将隐私说明部署到公开 HTTPS 地址
- 在应用商店填写内容分级、数据安全和联系方式
