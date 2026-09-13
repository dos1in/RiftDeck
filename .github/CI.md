# 自动化测试

`Android CI` 在 push、Pull Request 和手动运行时触发。同一分支或 PR 的新运行会取消旧运行。

## 检查内容

- JVM 单元测试：扫描、筛选、输入映射、模拟器参数、偏好设置等现有测试。
- Python 测试：Baseline Profile 转换工具。
- Android Lint、Debug APK、Release APK 和测试 APK 编译。Release 仅验证构建，不签名发布。
- Android 12（API 31）模拟器：运行 `LibraryDatabaseTest`，覆盖数据库迁移、增量扫描、收藏和历史保留等行为。

测试与 Lint 报告保存 14 天，成功构建的 Debug APK 保存 7 天，可从 Actions 运行详情下载。报告上传步骤在测试失败后也会执行。

## 本地运行

准备 JDK 17、Android SDK 36 和 Build Tools 35.0.0 后，在仓库根目录执行：

```sh
python3 -m unittest discover -s tools/performance -p 'test_*.py' -v
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest --continue
```

连接测试设备或启动模拟器后运行数据库测试：

```sh
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.riftdeck.data.LibraryDatabaseTest
```

## 验证边界

CI 不运行需要显式提供目录授权的 `SafPermissionIntegrationTest`，不访问用户 ROM，也不安装外部模拟器。实体方向键、SD 卡拔插、权限撤销、RetroArch 启动与返回、小屏可读性和性能仍需 KPA 真机验收。模拟器测试通过不能代替这些验证。

工作流使用只读仓库权限，不需要配置发布密钥。合入默认分支后，可在 GitHub 分支保护中将两个作业设置为合并前必需检查。
