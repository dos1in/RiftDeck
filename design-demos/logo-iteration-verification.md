# C 聚焦舞台 · Logo 品牌迭代验证

日期：2026-09-05。基于 `main` 的 `7b62597` 工作区修改，未提交或推送。用户原话及迭代依据见 [direction-approved.md](direction-approved.md)，资产和色板见 [brand-spec.md](brand-spec.md)。

## 结果

- 首页、游戏库、详情、设置共用原图图标和字标，使用纯黑背景、亮黄主操作、青色次强调及对向切角。
- 完整品牌板只保留为设计参考，APK 使用独立图标与字标；Android 在后台按显示尺寸采样并共享结果，没有新增依赖。
- 保留既有控制器导航、列表状态和减少动效设置。增加字号时，侧栏与游戏序号区域同步留出空间，修复 130% 字体下的标签截断和序号换行。
- [交互原型](C-rift-brand.html) 及 [总览](index.html) 已同步更新，原始三方向文件保留。

## 构建

配置 JDK 17 和 Android SDK，确保 `JAVA_HOME`、`ANDROID_HOME` 指向对应安装目录后，在仓库根目录执行：

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --offline --console=plain
```

最终结果：构建成功；现有 9 项单元测试通过；Lint 0 错误、5 条既有依赖版本提示；`git diff --check` 通过。APK 为 `app/build/outputs/apk/debug/app-debug.apk`，已保留数据安装到手机。

## Android 实际运行

手机为一加 PLK110，Android 16，横屏 2772×1272、560 dpi。模拟器为 Android 35 ARM64、160 dpi，使用 640×360 和 640×480 两种横屏逻辑尺寸。

| 检查 | 已观察结果 |
| --- | --- |
| 四个页面的品牌图、字标、焦点 | 原图完整显示，边框可见，主操作可达 |
| 方向键 → 开始 → A → B | 启动提示可打开，关闭后焦点回到开始按钮 |
| X 详情 → Y 收藏 → Y 恢复 → B | 收藏状态变化正确，返回保留焦点 |
| 列表选择 → X → B | 返回同一游戏行，已检查 Pocket Tactics |
| 连续 24 次下方向键 | 停在末行，焦点保持有效 |
| L1/R1 分类切换 | 焦点落在对应筛选入口 |
| 设置 → 减少动效 → A 开关 | 状态变化正确，测试后恢复原值 |
| 手机触控打开详情 → B | 详情主操作可聚焦，返回仍有有效焦点 |
| 16:9 → 4:3 → 16:9 | 页面和焦点保持；四页已截图检查 |
| 640×360、130% 字体 | 侧栏完整显示“游戏库”，序号保持一行，开始按钮可聚焦 |

最后一次安装后再次检查手机启动、详情和返回，应用停在首页。手机与模拟器的当前应用进程中未观察到 AndroidRuntime 错误或品牌图加载警告。

实机截图：

- [首页](screenshots/logo-phone/home.png)
- [游戏库](screenshots/logo-phone/library.png)
- [详情](screenshots/logo-phone/detail.png)
- [设置](screenshots/logo-phone/settings.png) / [外观](screenshots/logo-phone/appearance.png)
- [启动提示](screenshots/logo-phone/launch-dialog.png)

紧凑画布截图存于 `screenshots/logo-native/`，包括两种比例的四页及 [130% 字体首页](screenshots/logo-native/home-640x360-font130.png)。

## HTML 原型

Chrome / Playwright 打开本地单文件，检查四页和 640×480 画布，未出现页面脚本错误；两处品牌图均在可视区域。总览默认加载 `C-rift-brand.html`。浏览器截图使用 `screenshots/C-rift-brand-*.png`，与原生运行截图分开保存。

## 验证范围

手机按键检查通过 ADB 注入事件完成，尚未在 KONKR Pocket Advance 上验证实体手柄的长按、摇杆手感和输入延迟。未做帧率测量，也不把本轮 UI 检查视为真实 ROM 扫描、SD 卡或外部模拟器启动验收。当前仍使用示例游戏库；Android 桌面图标未在本轮更换。

## 独立素材修正

根据用户指出的“这张图不应该是完整的图片”，移除应用中的完整品牌板，改用 `riftdeck_emblem.webp`、`riftdeck_wordmark.webp`。删除设计板坐标、区域解码和 Android 版本分支；原型也不再嵌入完整品牌板。参考图只留在设计目录。

重新执行构建、9 项现有单测和 Lint；检查 APK ZIP 条目，仅有两个独立品牌资源。新包已安装到手机，补查首页、详情、方向键和返回焦点。修正后的截图见 [手机首页](screenshots/brand-assets/home-phone.png)、[手机详情](screenshots/brand-assets/detail-phone.png) 和 [原型首页](screenshots/brand-assets/home-prototype.png)。上方四页与紧凑布局截图保留为之前品牌迭代的验证记录。
