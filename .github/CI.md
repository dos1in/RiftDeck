# 自动化测试

`Android CI` 在 push、Pull Request 和手动运行时触发。同一分支或 PR 的新运行会取消旧运行。

## 检查内容

- JVM 单元测试：扫描、筛选、输入映射、模拟器参数、偏好设置等现有测试。
- Python 测试：Baseline Profile 转换工具和发布版本递增校验。
- Android Lint、Debug APK、Release APK 和测试 APK 编译。Release 仅验证构建，不签名发布。
- Android 12（API 31）模拟器：运行数据库、SAF 安全写入、局域网同步、模拟器写入互斥、更新协议和 APK 签名校验等集成测试。

测试与 Lint 报告保存 14 天，成功构建的 Debug APK 保存 7 天，可从 Actions 运行详情下载。报告上传步骤在测试失败后也会执行。

## 本地运行

准备 JDK 17、Android SDK 36 和 Build Tools 35.0.0 后，在仓库根目录执行：

```sh
python3 -m unittest discover -s tools/performance -p 'test_*.py' -v
python3 -m unittest discover -s tools/release -p 'test_*.py' -v
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest --continue
```

连接隔离测试设备或启动模拟器后运行全部 Android 集成测试：

```sh
./gradlew :app:connectedDebugAndroidTest
```

## 验证边界

需要显式提供目录授权的 `SafPermissionIntegrationTest` 在 CI 中跳过；测试不访问用户 ROM，也不安装外部模拟器。实体方向键、SD 卡拔插、权限撤销、RetroArch 启动与返回、小屏可读性和性能仍需 KPA 真机验收。模拟器测试通过不能代替这些验证。

工作流使用只读仓库权限，不需要配置发布密钥。合入默认分支后，可在 GitHub 分支保护中将两个作业设置为合并前必需检查。

## 签名 APK 发布与应用内更新

独立的 `Publish Android release` 工作流只在 Actions 中手动触发，具有创建 Release 所需的写权限。先配置仓库 Secrets：

- `RIFTDECK_KEYSTORE_BASE64`：现有发布签名 keystore 的 Base64 内容。
- `RIFTDECK_SIGNING_STORE_PASSWORD`、`RIFTDECK_SIGNING_KEY_ALIAS`、`RIFTDECK_SIGNING_KEY_PASSWORD`：该密钥的密码和别名。

填写稳定版本名（如 `0.1.1`）和大于所有既有发布版本的 Android 版本号（如 `2`）。工作流运行单元测试和 Lint，验证签名后发布 `v0.1.1` 标签及 `RiftDeck-v0.1.1.apk`。GitHub 为附件提供 SHA-256 摘要，应用使用该摘要校验下载。已有调试安装使用调试签名，不能直接覆盖安装采用另一签名的正式包。

发布前读取全部正式稳定 Release，要求版本名高于历史标签，并通过 SDK `aapt` 检查已有官方 APK 的实际版本号，拒绝重复或回退版本。首次发布没有历史 APK 时正常通过；历史检查失败会停止发布。

本地签名构建从环境变量读取 `RIFTDECK_SIGNING_KEYSTORE`（文件路径）及上述三个密码／别名变量：

```sh
./gradlew :app:assembleRelease -PappVersionName=0.1.1 -PappVersionCode=2
```

未提供 `RIFTDECK_SIGNING_KEYSTORE` 时，既有 CI 继续生成未签名 Release APK 以验证编译。本次仅增加发布能力，不创建发布密钥或执行远程发布。

Android 测试构建还会生成几个小型签名 APK 测试夹具，验证更新的包名、版本和签名。夹具从源码生成，位于构建目录，仅进入测试 APK；不会安装到用户设备或包含在应用 APK 中。
