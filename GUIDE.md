# RiftDeck 使用与开发指南

[返回首页](README.md) · **简体中文** | [English](GUIDE.en.md)

## 当前功能

* 手柄导航：方向键 / 摇杆移动，A/B/X/Y 执行动作，L1/R1 切换分类，L2/R2 翻页，并支持按当前显示标题顺序进行首字母跳转。
* 首页、游戏库、游戏详情，以及适合手柄操作的设置页面。
* 固定暗色裂隙配色，支持减少动态效果和收起左侧导航。偏好设置使用 DataStore 保存。
* 支持设为 Android 默认桌面。进入 **设置 → 系统桌面 → 设为默认桌面**，由 Android 请求确认；取消后保留原有默认桌面。
* 持久化 SAF 文件夹访问权限，递归扫描 `.gba` / `.zip`，支持增量更新、取消扫描和已删除文件检测。
* 使用 Room 保存游戏库，支持收藏、最近游玩、排序，以及按游戏名称 / 文件名搜索。搜索提供手柄键盘，触屏输入可使用系统键盘。
* 选择已安装的模拟器并保存配置。已使用自制诊断 ROM 验证 GBA.emu 的 SAF 启动和 ZIP 解压启动流程。
* 记录启动次数、最近游玩时间和估算游玩时长；从模拟器返回时恢复页面、选中项和滚动位置。

首次安装时游戏库为空。选择 **添加 ROM 文件夹**，在 Android 文件夹选择器中授予访问权限，然后在 **设置 → 模拟器** 中选择已安装的 GBA 模拟器。请使用你有权运行的游戏文件。Android 文件选择器和外部模拟器各自有独立的输入与存储设置。请在模拟器中配置可写的存档目录；RiftDeck 仅授予 ROM 读取权限，不管理模拟器存档。

通用模拟器的 ZIP 解压流程要求文件只包含一个 GBA ROM。应用会拒绝不安全的路径、无法明确选择 ROM 的压缩包和超大文件。解压后的 ROM 存放于私有临时缓存，通过 FileProvider 授予有限的读取权限；前端恢复运行或准备下一个 ROM 时，会清理已超过 24 小时的旧缓存文件。

当前去重可识别通过重叠文件夹重复导入的同一文档。内容相同但文档 ID 不同的副本仍会分别保留。游玩时长按启动到返回的时间间隔估算，单次上限为 24 小时，无法区分模拟器暂停或后台运行的时间。Android 成功发起启动，并不保证模拟器能接受每个 ROM。

本地封面使用 Coil 加载。将同名 PNG、WebP、JPG 或 JPEG 放在 ROM 旁边后重新扫描，例如 `Game.gba` 和 `Game.png`。匹配时忽略大小写、保留地区标记；存在多个封面时，依次优先使用 PNG、WebP、JPG、JPEG。替换或删除封面会在下次扫描时更新；无法访问或损坏的图片会回退为几何占位图。

支持导入本地 Pegasus 游戏介绍、封面和视频；设置中可选择图片或视频预览、等待时间及循环播放。RetroArch G · mGBA 可直接读取原始 GBA / ZIP，前端检查可读性，压缩包内容由 RetroArch 处理，以保留原有存档路径关联。在线元数据刮削、更多模拟器专用适配器和 Android 应用抽屉仍在计划中。

验证记录和待完成的硬件检查见 [CORE_IMPLEMENTATION.md](CORE_IMPLEMENTATION.md)。

## 目标设备

### KONKR Pocket Advance

KONKR Pocket Advance 是 RiftDeck 开发的首个参考设备。

界面围绕紧凑的横屏掌机显示屏和实体按键设计。

RiftDeck 不局限于单一设备，UI 架构旨在适配不同的：

* 屏幕尺寸
* 屏幕比例
* 像素密度
* 手柄布局
* Android 游戏掌机

计划支持的布局比例包括：

* 4:3
* 16:9
* 16:10

## 设计

RiftDeck 希望带来专用游戏主机般的操作体验。

默认视觉风格包括：

* 深色界面
* 高对比度文字
* 霓虹黄色焦点强调
* 青色与品红色辅助强调
* 几何切角与技术感线条
* 克制的未来感 HUD 元素
* 清晰的手柄焦点指示

视觉效果保持轻量，响应速度和可读性始终优先于装饰。

## 技术栈

已使用 Kotlin、Jetpack Compose、Navigation Compose、Room / KSP、Coil、Coroutines / Flow 和 DataStore。ROM 扫描与 ZIP 处理均使用 Kotlin，在后台调度器上执行。

Media3 视频预览已实现；Baseline Profiles 和宏基准测试仍在计划中。目前没有原生核心；只有性能分析证明有必要时，才会考虑引入 C++。

## 架构

RiftDeck 采用分层架构。

```text
Compose UI
    │
    ▼
ViewModel
    │
    ▼
Use Cases
    │
    ▼
Repository
    │
    ├── Room
    ├── Android APIs
    ├── Storage
    ├── Metadata Providers
    └── Emulator Integration
            │
            ▼
      Native Core
        （可选）
```

推荐的项目结构：

```text
app/
core/
data/
domain/
feature/
```

UI、存储、模拟器集成、ROM 扫描和平台专属逻辑保持分离。

详细架构与开发规范见 [AGENTS.md](AGENTS.md)。

## 性能

性能是项目的核心要求。RiftDeck 围绕以下目标设计：

* 快速启动
* 低输入延迟
* 流畅的游戏网格滚动
* 后台 ROM 扫描
* 增量数据库更新
* 按显示尺寸解码图片
* 延迟启动视频预览
* 尽量减少 Android 主线程上的工作

即使游戏库较大，也应保持流畅操作。

只有性能分析定位到实际瓶颈后，才会引入原生代码优化。

## 存储

RiftDeck 优先使用 Android 存储访问框架（Storage Access Framework）。

应用设计支持：

* 内部存储
* SD 卡
* 可移除存储
* 多个 ROM 目录
* 持久化文件夹访问权限

ROM 文件保留在用户设备上。

## 本地优先

RiftDeck 以本地使用为核心，基础功能不需要账号或在线服务，游戏库默认保存在本地。

可以选择使用元数据服务获取：

* 游戏标题
* 封面
* 截图
* 游戏简介
* 发行信息

RiftDeck 不上传 ROM 文件。

## 路线图

应用框架、本地 GBA 游戏库、外部模拟器启动、本地元数据与封面、可选视频预览已实现。目标掌机已验证十字键导航、SD 卡插拔恢复和 RetroArch 退出返回。后续工作包括发布性能分析、在线元数据抓取和更多游戏平台支持。

## 构建

使用 JDK 17 和 Android SDK 36。仓库已包含 Gradle Wrapper；Room 数据库结构文件在 `app/schemas` 中纳入版本管理。

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

第二条命令需要连接 Android 测试设备或模拟器。仪器化测试请使用独立测试设备。`tools/test-fixtures` 提供可从源码构建的诊断 ROM，不含第三方游戏数据。

## 参与贡献

RiftDeck 仍处于早期开发阶段。

欢迎提交代码、反馈问题、讨论设计、分享模拟器配置和设备兼容性报告，以及改进性能。

修改代码前，请先阅读 [AGENTS.md](AGENTS.md)。

项目的主要原则：

1. 手柄导航优先。
2. 性能本身就是功能。
3. 核心功能保持本地优先。
4. 避免不必要的依赖。
5. 没有测量依据，不引入原生代码。
6. 平台与模拟器集成保持模块化。
7. 面向真实掌机优化，不能只依赖 Android 模拟器验证。

## 致谢

RiftDeck 的灵感来自开源模拟器前端的长期积累，以及复古掌机社区。

RiftDeck 本身是前端，不包含模拟器核心或受版权保护的游戏内容。
