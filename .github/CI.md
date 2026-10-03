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

填写稳定版本名（如 `0.1.1`）和大于所有既有发布版本的 Android 版本号（如 `2`）。工作流运行单元测试和 Lint，验证签名后发布 `v0.1.1` 标签及 `RiftDeck-v0.1.1.apk`。GitHub 为附件提供 SHA-256 摘要，应用使用该摘要校验下载。覆盖安装要求包名和签名兼容；本地 Debug 和 Release 使用下述固定配置时签名相同，早先使用其他机器默认 Debug 密钥的安装仍不能被直接覆盖。

发布前读取全部正式稳定 Release，要求版本名高于历史标签，并通过 SDK `aapt` 检查已有官方 APK 的实际版本号，拒绝重复或回退版本。首次发布没有历史 APK 时正常通过；历史检查失败会停止发布。

### 多台电脑使用同一签名

多台电脑开发时，安全分发同一份签名密钥，并分别创建本地配置：

```sh
cp signing.properties.example signing.properties
chmod 600 signing.properties
```

模板只包含占位值，请在本地填写密钥位置、别名及密码。`storeFile` 支持用户主目录、绝对路径以及相对于仓库根目录的路径。本地配置和密钥文件已被 Git 忽略；密钥应限制为当前用户读取，并另外保存加密备份。换电脑时需要使用同一份密钥，仅复制配置文件无法得到相同签名。

存在此配置时，Debug、Release 和 Debug 测试 APK 都使用它：

```sh
./gradlew :app:assembleDebug :app:assembleRelease
./gradlew :app:signingReport
```

各台机器报告的 Debug / Release SHA-256 应相同。版本升级仍需保持 `applicationId` 并递增 `appVersionCode`：

```sh
./gradlew :app:assembleRelease -PappVersionName=0.1.1 -PappVersionCode=2
```

本地配置缺失且未设置签名环境变量时，构建保持 Android 默认 Debug 签名及未签名 Release，供普通 CI 检查编译。存在配置但缺少字段或密钥文件时直接报错，避免意外换成其他签名。

CI 发布或其他自动化可继续设置 `RIFTDECK_SIGNING_KEYSTORE`（文件路径）、`RIFTDECK_SIGNING_STORE_PASSWORD`、`RIFTDECK_SIGNING_KEY_ALIAS`、`RIFTDECK_SIGNING_KEY_PASSWORD`。只要设置了 `RIFTDECK_SIGNING_KEYSTORE`，这组环境变量就整体覆盖本地配置，四项都必须完整；Debug 和 Release 都使用这组签名。GitHub 发布 Secrets 也必须来自同一份密钥，才能覆盖安装本地构建。配置本身不会执行远程发布。

Android 测试构建还会生成几个小型签名 APK 测试夹具，验证更新的包名、版本和签名。匹配夹具读取 Debug 实际使用的密钥、别名及密码；错误签名夹具使用独立的临时测试密钥。夹具从源码生成，位于构建目录，仅进入测试 APK；不会安装到用户设备或包含在应用 APK 中。
